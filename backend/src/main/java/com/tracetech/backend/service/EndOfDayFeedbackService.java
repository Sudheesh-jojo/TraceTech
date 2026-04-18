package com.tracetech.backend.service;

import com.tracetech.backend.dto.FeedbackSummary;
import com.tracetech.backend.dto.FeedbackSummary.ItemAccuracy;
import com.tracetech.backend.entity.*;
import com.tracetech.backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EndOfDayFeedbackService — Step 5 core engine.
 *
 * Runs at end of day after the vendor submits actuals.
 * Zero changes to any existing service, controller, or ML code.
 *
 * What it does in one transaction:
 *   1. Saves DailySalesRecord rows (predicted vs actual per item)
 *   2. Writes AccuracyLog rows with signed + absolute error percentages
 *   3. Deducts actual consumed ingredient quantities from inventory
 *   4. Returns FeedbackSummary DTO including daily MAPE
 *
 * FIX: Uses ForecastRepository (not ImpactSummaryRepository) to read predictions,
 * because the existing ImpactSummary entity is a daily aggregate without per-item
 * menuItem/predictedQty fields. Forecast table has exactly what we need:
 * per-item-per-date predicted quantities written by ForecastService.
 */
@Service
@Slf4j
public class EndOfDayFeedbackService {

    private final DailySalesRecordRepository salesRecordRepo;
    private final AccuracyLogRepository      accuracyRepo;
    private final InventoryRepository        inventoryRepo;
    private final RecipeRepository           recipeRepo;
    private final MenuItemRepository         menuItemRepo;
    private final ForecastRepository         forecastRepo;

    @Value("${tracetech.accuracy.threshold-pct}")
    private BigDecimal accuracyThresholdPct;

    public EndOfDayFeedbackService(DailySalesRecordRepository salesRecordRepo,
                                   AccuracyLogRepository accuracyRepo,
                                   InventoryRepository inventoryRepo,
                                   RecipeRepository recipeRepo,
                                   MenuItemRepository menuItemRepo,
                                   ForecastRepository forecastRepo) {
        this.salesRecordRepo = salesRecordRepo;
        this.accuracyRepo    = accuracyRepo;
        this.inventoryRepo   = inventoryRepo;
        this.recipeRepo      = recipeRepo;
        this.menuItemRepo    = menuItemRepo;
        this.forecastRepo    = forecastRepo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param date    the trading date being closed out
     * @param actuals map of { menuItemId → actualQtySold }
     */
    @Transactional
    public FeedbackSummary processEndOfDay(LocalDate date, Map<Long, Integer> actuals) {

        List<Long> itemIds = new ArrayList<>(actuals.keySet());

        // ── Bulk data load — minimal DB round trips, never N+1 ───────────────
        Map<Long, MenuItem> itemMap = menuItemRepo.findAllById(itemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, i -> i));

        // FIX: Get predicted quantities from Forecast table (written by ForecastService)
        // instead of ImpactSummary (which is a daily aggregate without per-item data).
        Map<Long, Integer> predictedMap = forecastRepo.findByForecastDate(date).stream()
                .filter(f -> itemIds.contains(f.getMenuItem().getId()))
                .collect(Collectors.toMap(
                        f -> f.getMenuItem().getId(),
                        Forecast::getPredictedQty,
                        (a, b) -> a  // in case of duplicates, take first
                ));

        Map<Long, List<Recipe>> recipesByItem = recipeRepo.findByMenuItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(r -> r.getMenuItem().getId()));

        Map<String, Inventory> inventoryMap = inventoryRepo
                .findByIngredientNameIn(collectIngredientNames(recipesByItem)).stream()
                .collect(Collectors.toMap(Inventory::getIngredientName, i -> i));

        // ── Process each item ────────────────────────────────────────────────
        List<DailySalesRecord> salesRecords  = new ArrayList<>();
        List<AccuracyLog>      accuracyLogs  = new ArrayList<>();
        List<ItemAccuracy>     itemBreakdown = new ArrayList<>();
        BigDecimal             totalDeducted = BigDecimal.ZERO;

        for (Long itemId : itemIds) {
            Integer actual    = actuals.get(itemId);
            Integer predicted = predictedMap.getOrDefault(itemId, 0);
            MenuItem item     = itemMap.get(itemId);
            if (item == null || actual == null) continue;

            // Step 1: sales record (predicted vs actual side by side)
            salesRecords.add(buildSalesRecord(date, item, predicted, actual));

            // Step 2: accuracy log (signed + absolute error percentages)
            BigDecimal errorPct    = computeErrorPct(predicted, actual);
            BigDecimal absErrorPct = errorPct.abs();
            accuracyLogs.add(buildAccuracyLog(date, item, predicted, actual, errorPct, absErrorPct));

            // Step 3: deduct consumed ingredients from inventory
            List<Recipe> recipes = recipesByItem.getOrDefault(itemId, Collections.emptyList());
            totalDeducted = totalDeducted.add(deductInventory(actual, recipes, inventoryMap));

            itemBreakdown.add(new ItemAccuracy(
                    itemId, item.getName(), predicted, actual, errorPct, absErrorPct, accuracyThresholdPct));
        }

        // Batch persist — inventory is mutated in-memory then saved here
        salesRecordRepo.saveAll(salesRecords);
        accuracyRepo.saveAll(accuracyLogs);
        inventoryRepo.saveAll(inventoryMap.values());

        log.info("EOD feedback processed for {} — {} items, MAPE will be in summary",
                date, itemBreakdown.size());

        return buildSummary(date, itemBreakdown, totalDeducted);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public read methods (called by FeedbackController)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rolling 30-day MAPE per item.
     * Consumed by the ML retraining script to identify which items need extra attention.
     * Returns { menuItemId → rollingMape }
     */
    public Map<Long, BigDecimal> getRollingMape() {
        LocalDate since = LocalDate.now().minusDays(30);
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : accuracyRepo.findRollingMapeByItem(since)) {
            result.put((Long) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private DailySalesRecord buildSalesRecord(LocalDate date, MenuItem item,
                                               int predicted, int actual) {
        DailySalesRecord r = new DailySalesRecord();
        r.setSaleDate(date);
        r.setMenuItem(item);
        r.setPredictedQty(predicted);
        r.setActualQty(actual);
        return r;
    }

    private AccuracyLog buildAccuracyLog(LocalDate date, MenuItem item,
                                          int predicted, int actual,
                                          BigDecimal errorPct, BigDecimal absErrorPct) {
        AccuracyLog log = new AccuracyLog();
        log.setLogDate(date);
        log.setMenuItem(item);
        log.setPredictedQty(predicted);
        log.setActualQty(actual);
        log.setAccuracyPct(errorPct);
        log.setAbsErrorPct(absErrorPct);
        return log;
    }

    /**
     * Deducts actual consumption from inventory in-memory.
     * inventoryMap is mutated here and persisted in bulk after the main loop.
     * Uses max(0, stock - consumed) — never go negative.
     *
     * @return total ingredient units deducted (for the summary DTO)
     */
    private BigDecimal deductInventory(int actualQty, List<Recipe> recipes,
                                        Map<String, Inventory> inventoryMap) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal qty   = BigDecimal.valueOf(actualQty);

        for (Recipe recipe : recipes) {
            Inventory inv = inventoryMap.get(recipe.getIngredientName());
            if (inv == null) continue;

            BigDecimal consumed = recipe.getQtyPerUnit().multiply(qty);
            BigDecimal newStock = inv.getCurrentQty().subtract(consumed).max(BigDecimal.ZERO);
            inv.setCurrentQty(newStock);
            total = total.add(consumed);
        }
        return total;
    }

    /**
     * Signed percentage error: (actual - predicted) / predicted × 100
     * Negative means over-predicted; positive means under-predicted.
     */
    private BigDecimal computeErrorPct(int predicted, int actual) {
        if (predicted == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(actual - predicted)
                .divide(BigDecimal.valueOf(predicted), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private List<String> collectIngredientNames(Map<Long, List<Recipe>> recipesByItem) {
        return recipesByItem.values().stream()
                .flatMap(Collection::stream)
                .map(Recipe::getIngredientName)
                .distinct()
                .collect(Collectors.toList());
    }

    private FeedbackSummary buildSummary(LocalDate date, List<ItemAccuracy> breakdown,
                                          BigDecimal totalDeducted) {
        BigDecimal mape = breakdown.isEmpty() ? BigDecimal.ZERO
                : breakdown.stream()
                        .map(ItemAccuracy::getAbsErrorPct)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(breakdown.size()), 2, RoundingMode.HALF_UP);

        FeedbackSummary summary = new FeedbackSummary();
        summary.setDate(date);
        summary.setItemsProcessed(breakdown.size());
        summary.setDailyMape(mape);
        summary.setInventoryDeducted(totalDeducted);
        summary.setItemBreakdown(breakdown);
        return summary;
    }
}
