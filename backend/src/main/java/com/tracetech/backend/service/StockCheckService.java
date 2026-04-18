package com.tracetech.backend.service;

import com.tracetech.backend.dto.IngredientRequirement;
import com.tracetech.backend.dto.StockCheckResult;
import com.tracetech.backend.entity.Inventory;
import com.tracetech.backend.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * StockCheckService — Step 2 of TraceTech supply chain.
 *
 * INTEGRATION CONTRACT (read carefully — no existing code is modified):
 *
 *   Input:  Map<String, IngredientRequirement>
 *           — produced by RecipeService.calculateDailyIngredientNeeds()
 *           — call that first, pass the result here
 *
 *   Output: StockCheckSummary
 *           — contains per-ingredient StockCheckResult list
 *           — split into ok / low / shortfall buckets for easy frontend rendering
 *           — canProceed flag tells ForecastController whether stock is fine
 *
 * This service NEVER modifies ForecastService, RecipeService, or any ML logic.
 * It reads Inventory and IngredientRequirement — that's it.
 */
@Service
@Transactional(readOnly = true)
public class StockCheckService {

    private final InventoryRepository inventoryRepository;

    public StockCheckService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // -------------------------------------------------------------------------
    // CORE: Run stock check against today's ingredient requirements
    //
    // Usage in ForecastService (add these 2 lines after existing forecast logic):
    //   Map<String, IngredientRequirement> needs = recipeService.calculateDailyIngredientNeeds(predictedQtyMap);
    //   StockCheckSummary stockCheck = stockCheckService.checkStock(needs);
    // -------------------------------------------------------------------------
    public StockCheckSummary checkStock(Map<String, IngredientRequirement> ingredientNeeds) {

        // Single DB call — fetch all inventory rows for needed ingredients
        List<String> ingredientNames = new ArrayList<>(ingredientNeeds.keySet());
        List<Inventory> inventoryRows = inventoryRepository.findByIngredientNameIn(ingredientNames);

        // Build lookup map: ingredientName → Inventory row
        Map<String, Inventory> inventoryMap = inventoryRows.stream()
                .collect(Collectors.toMap(Inventory::getIngredientName, inv -> inv));

        // Compare each need against available stock
        List<StockCheckResult> results = new ArrayList<>();

        for (Map.Entry<String, IngredientRequirement> entry : ingredientNeeds.entrySet()) {
            String name = entry.getKey();
            IngredientRequirement req = entry.getValue();

            Inventory inv = inventoryMap.get(name);

            if (inv == null) {
                // No inventory row exists for this ingredient
                results.add(StockCheckResult.missing(name, req.getTotalQtyNeeded()));
            } else {
                StockCheckResult result = new StockCheckResult(
                        name,
                        inv.getUnit(),
                        req.getTotalQtyNeeded(),
                        inv.getCurrentQty(),
                        inv.getSafetyBufferPct()
                );
                results.add(result);
            }
        }

        return new StockCheckSummary(results);
    }

    // -------------------------------------------------------------------------
    // CONVENIENCE: Check stock for a single ingredient (used by admin UI)
    // -------------------------------------------------------------------------
    public Optional<StockCheckResult> checkSingleIngredient(String ingredientName, BigDecimal needed) {
        return inventoryRepository.findByIngredientName(ingredientName)
                .map(inv -> new StockCheckResult(
                        ingredientName,
                        inv.getUnit(),
                        needed,
                        inv.getCurrentQty(),
                        inv.getSafetyBufferPct()
                ));
    }

    // -------------------------------------------------------------------------
    // READ: Get all low-stock items (for dashboard alert card)
    // Completely independent — does not need RecipeService input
    // -------------------------------------------------------------------------
    public List<Inventory> getLowStockItems() {
        return inventoryRepository.findLowStockItems();
    }

    // -------------------------------------------------------------------------
    // WRITE: Vendor logs a restock delivery (adds to current_qty)
    // -------------------------------------------------------------------------
    @Transactional
    public boolean restock(String ingredientName, BigDecimal addedQty) {
        int updated = inventoryRepository.addStock(ingredientName, addedQty);
        return updated > 0;
    }

    // -------------------------------------------------------------------------
    // WRITE: Deduct stock after confirmed daily production
    // Called at end of day when vendor submits actual sales
    // -------------------------------------------------------------------------
    @Transactional
    public boolean deductStock(String ingredientName, BigDecimal usedQty) {
        int updated = inventoryRepository.deductStock(ingredientName, usedQty);
        return updated > 0;
    }

    // -------------------------------------------------------------------------
    // VALIDATION: Find ingredients in recipes that have no inventory row
    // Useful at setup time — call GET /api/inventory/missing
    // -------------------------------------------------------------------------
    public List<String> findIngredientsMissingFromInventory(List<String> allRecipeIngredients) {
        List<String> existing = inventoryRepository.findAllIngredientNames();
        return allRecipeIngredients.stream()
                .filter(name -> !existing.contains(name))
                .sorted()
                .collect(Collectors.toList());
    }

    // =========================================================================
    // INNER CLASS: Summary wrapping all StockCheckResult rows
    // Returned by checkStock() — used directly in controller responses
    // =========================================================================
    public static class StockCheckSummary {

        private final List<StockCheckResult> all;
        private final List<StockCheckResult> ok;
        private final List<StockCheckResult> low;
        private final List<StockCheckResult> shortfalls;
        private final boolean canProceed; // true = stock OK or only LOW (no hard shortfalls)

        public StockCheckSummary(List<StockCheckResult> results) {
            this.all = results;
            this.ok         = results.stream().filter(StockCheckResult::isOk).collect(Collectors.toList());
            this.low        = results.stream().filter(StockCheckResult::isLow).collect(Collectors.toList());
            this.shortfalls = results.stream().filter(StockCheckResult::isShortfall).collect(Collectors.toList());
            this.canProceed = shortfalls.isEmpty();
        }

        public List<StockCheckResult> getAll()        { return all; }
        public List<StockCheckResult> getOk()         { return ok; }
        public List<StockCheckResult> getLow()        { return low; }
        public List<StockCheckResult> getShortfalls() { return shortfalls; }
        public boolean isCanProceed()                  { return canProceed; }
        public int getTotalIngredients()               { return all.size(); }
        public int getShortfallCount()                 { return shortfalls.size(); }
        public int getLowCount()                       { return low.size(); }
    }
}
