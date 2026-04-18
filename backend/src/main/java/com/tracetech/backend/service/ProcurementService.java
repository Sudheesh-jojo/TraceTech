package com.tracetech.backend.service;

import com.tracetech.backend.dto.IngredientRequirement;
import com.tracetech.backend.dto.ProcurementOrderItem;
import com.tracetech.backend.dto.ProcurementOrderItem.Urgency;
import com.tracetech.backend.dto.StockCheckResult;
import com.tracetech.backend.entity.IngredientSupplier;
import com.tracetech.backend.repository.IngredientSupplierRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProcurementService — Step 3 of TraceTech supply chain.
 *
 * INTEGRATION CONTRACT:
 *
 *   Inputs (both from previous steps — no re-computation needed):
 *     1. List<StockCheckResult>         — shortfalls from StockCheckService.checkStock()
 *     2. Map<String, IngredientRequirement> — daily needs from RecipeService (optional, for lead-time calc)
 *
 *   Output: ProcurementPlan
 *     — List<ProcurementOrderItem> sorted by urgency
 *     — totalEstimatedCost
 *     — criticalCount, highCount, mediumCount
 *
 * DOES NOT MODIFY: ForecastService, RecipeService, StockCheckService, any ML code.
 *
 * HOW TO CALL from a controller or ForecastService (2 lines):
 *   StockCheckSummary stock = stockCheckService.checkStock(needs);
 *   ProcurementPlan plan    = procurementService.buildProcurementPlan(stock.getShortfalls(), needs);
 */
@Service
@Transactional(readOnly = true)
public class ProcurementService {

    private final IngredientSupplierRepository supplierRepository;

    @Value("${tracetech.procurement.default-cost}")
    private String defaultCostPerUnit;

    @Value("${tracetech.procurement.default-supplier}")
    private String defaultSupplierName;

    public ProcurementService(IngredientSupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    // -------------------------------------------------------------------------
    // CORE: Build the full procurement plan from shortfalls + daily needs
    // -------------------------------------------------------------------------
    public ProcurementPlan buildProcurementPlan(
            List<StockCheckResult> shortfalls,
            Map<String, IngredientRequirement> dailyNeeds) {

        if (shortfalls == null || shortfalls.isEmpty()) {
            return ProcurementPlan.empty();
        }

        LocalDate today = LocalDate.now();

        // Single DB call — fetch supplier info for all shortfall ingredients at once
        List<String> names = shortfalls.stream()
                .map(StockCheckResult::getIngredientName)
                .collect(Collectors.toList());

        Map<String, IngredientSupplier> supplierMap =
                supplierRepository.findByIngredientNameIn(names).stream()
                        .collect(Collectors.toMap(
                                IngredientSupplier::getIngredientName,
                                s -> s
                        ));

        List<ProcurementOrderItem> items = new ArrayList<>();

        for (StockCheckResult sr : shortfalls) {
            String name = sr.getIngredientName();
            IngredientSupplier supplier = supplierMap.get(name);

            // Determine daily average for lead-time cover calculation
            BigDecimal dailyAvg = BigDecimal.ZERO;
            if (dailyNeeds != null && dailyNeeds.containsKey(name)) {
                dailyAvg = dailyNeeds.get(name).getTotalQtyNeeded();
            }

            // If no supplier row, use configurable defaults from application.properties
            BigDecimal costPerUnit  = supplier != null ? supplier.getCostPerUnit()  : new BigDecimal(defaultCostPerUnit);
            int leadTimeDays        = supplier != null ? supplier.getLeadTimeDays() : 0;
            String supplierName     = supplier != null ? supplier.getSupplierName() : defaultSupplierName;
            BigDecimal minOrderQty  = supplier != null ? supplier.getMinOrderQty()  : BigDecimal.ONE;

            // Lead time cover = daily_avg * lead_time_days
            // If supplier takes 2 days, we need 2 more days of stock to bridge the gap
            BigDecimal leadTimeCover = dailyAvg
                    .multiply(BigDecimal.valueOf(leadTimeDays))
                    .setScale(3, RoundingMode.HALF_UP);

            // Safety buffer on top of what we already computed in StockCheckResult
            BigDecimal safetyBuffer = sr.getSafetyBuffer();

            // Determine urgency
            Urgency urgency = determineUrgency(sr, leadTimeDays);

            ProcurementOrderItem item = new ProcurementOrderItem(
                    name,
                    sr.getUnit() != null ? sr.getUnit() : "units",
                    supplierName,
                    sr.getAvailable(),
                    sr.getNeeded(),
                    sr.getShortfall(),
                    safetyBuffer,
                    leadTimeCover,
                    costPerUnit,
                    minOrderQty,
                    leadTimeDays,
                    urgency,
                    today
            );

            items.add(item);
        }

        // Sort: CRITICAL first, then HIGH, MEDIUM, LOW
        items.sort(Comparator.comparing(ProcurementOrderItem::getUrgency));

        return new ProcurementPlan(items);
    }

    // -------------------------------------------------------------------------
    // URGENCY LOGIC
    //
    // CRITICAL = hard shortfall (can't produce today) AND supplier takes 2+ days
    //            → must order immediately — may already be too late
    // HIGH     = hard shortfall AND supplier is same/next day
    //            → order this morning, arrives in time
    // MEDIUM   = stock is LOW (covers today, not buffer)
    //            → order today to protect tomorrow
    // LOW      = MISSING supplier row but stock technically OK
    //            → informational, order when convenient
    // -------------------------------------------------------------------------
    private Urgency determineUrgency(StockCheckResult sr, int leadTimeDays) {
        if (sr.getStatus() == StockCheckResult.StockStatus.MISSING) {
            return Urgency.HIGH; // Unknown stock = treat as high priority
        }
        if (sr.getStatus() == StockCheckResult.StockStatus.SHORTFALL) {
            return leadTimeDays >= 2 ? Urgency.CRITICAL : Urgency.HIGH;
        }
        if (sr.getStatus() == StockCheckResult.StockStatus.LOW) {
            return Urgency.MEDIUM;
        }
        return Urgency.LOW;
    }

    // -------------------------------------------------------------------------
    // CONVENIENCE: Generate plan directly from a predicted qty map
    // This chains Steps 1 + 2 + 3 in a single call — useful for the controller
    // -------------------------------------------------------------------------
    public ProcurementPlan buildPlanFromPredictions(
            Map<Long, Integer> predictedQtyByItemId,
            RecipeService recipeService,
            StockCheckService stockCheckService) {

        Map<String, IngredientRequirement> needs =
                recipeService.calculateDailyIngredientNeeds(predictedQtyByItemId);

        StockCheckService.StockCheckSummary stockSummary =
                stockCheckService.checkStock(needs);

        return buildProcurementPlan(stockSummary.getShortfalls(), needs);
    }

    // -------------------------------------------------------------------------
    // READ: Get supplier info for one ingredient
    // -------------------------------------------------------------------------
    public Optional<IngredientSupplier> getSupplier(String ingredientName) {
        return supplierRepository.findByIngredientName(ingredientName);
    }

    // -------------------------------------------------------------------------
    // READ: Get all supplier rows (for admin management page)
    // -------------------------------------------------------------------------
    public List<IngredientSupplier> getAllSuppliers() {
        return supplierRepository.findAllByOrderByLeadTimeDaysDesc();
    }

    // -------------------------------------------------------------------------
    // READ: Find ingredients with no supplier entry yet
    // -------------------------------------------------------------------------
    public List<String> getMissingSuppliers() {
        return supplierRepository.findInventoryIngredientsMissingSupplier();
    }

    // -------------------------------------------------------------------------
    // WRITE: Add or update a supplier for an ingredient
    // -------------------------------------------------------------------------
    @Transactional
    public IngredientSupplier saveSupplier(IngredientSupplier supplier) {
        return supplierRepository.save(supplier);
    }

    // =========================================================================
    // INNER CLASS: Full procurement plan wrapping all order items
    // =========================================================================
    public static class ProcurementPlan {

        private final List<ProcurementOrderItem> items;
        private final BigDecimal totalEstimatedCost;
        private final long criticalCount;
        private final long highCount;
        private final long mediumCount;
        private final boolean hasUrgentItems;

        public ProcurementPlan(List<ProcurementOrderItem> items) {
            this.items = items;
            this.totalEstimatedCost = items.stream()
                    .map(ProcurementOrderItem::getEstimatedCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            this.criticalCount  = items.stream().filter(i -> i.getUrgency() == Urgency.CRITICAL).count();
            this.highCount      = items.stream().filter(i -> i.getUrgency() == Urgency.HIGH).count();
            this.mediumCount    = items.stream().filter(i -> i.getUrgency() == Urgency.MEDIUM).count();
            this.hasUrgentItems = criticalCount > 0 || highCount > 0;
        }

        public static ProcurementPlan empty() {
            return new ProcurementPlan(Collections.emptyList());
        }

        public List<ProcurementOrderItem> getItems()             { return items; }
        public BigDecimal getTotalEstimatedCost()                { return totalEstimatedCost; }
        public long getCriticalCount()                           { return criticalCount; }
        public long getHighCount()                               { return highCount; }
        public long getMediumCount()                             { return mediumCount; }
        public boolean isHasUrgentItems()                        { return hasUrgentItems; }
        public int getTotalItems()                               { return items.size(); }
        public boolean isEmpty()                                 { return items.isEmpty(); }
    }
}
