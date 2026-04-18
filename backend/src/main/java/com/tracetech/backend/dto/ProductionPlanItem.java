package com.tracetech.backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Production decision for one menu item.
 *
 * limitingFactor tells the vendor exactly WHY production was capped:
 *   NONE            — full demand can be met, no constraint hit
 *   STALL_CAPACITY  — stall total units/day cap was the bottleneck
 *   PER_ITEM_CAP    — per-item cap within stall was the bottleneck
 *   INGREDIENT      — a specific ingredient ran out first (ingredientBottleneck names it)
 *
 * wasteRisk:
 *   LOW    — actual_produce <= predicted_qty (no overproduction risk)
 *   MEDIUM — actual_produce > predicted_qty * 1.05 (slight overproduction)
 *   HIGH   — actual_produce > predicted_qty * 1.15 (significant overproduction)
 *            (this happens when vendor manually overrides the plan)
 */
public class ProductionPlanItem {

    public enum LimitingFactor { NONE, STALL_CAPACITY, PER_ITEM_CAP, INGREDIENT }
    public enum WasteRisk      { LOW, MEDIUM, HIGH }

    private Long   menuItemId;
    private String menuItemName;
    private int    stallId;
    private String stallName;

    private int predictedQty;        // from ML ensemble (Step 1)
    private int ingredientMaxUnits;  // max producible given current stock
    private int stallCapMax;         // stall capacity limit for this item
    private int actualProduce;       // final: min(predicted, ingredientMax, stallCap)

    private LimitingFactor limitingFactor;
    private String ingredientBottleneck; // set when limitingFactor = INGREDIENT
    private WasteRisk wasteRisk;

    // Cost fields — useful for profit estimation
    private BigDecimal ingredientCostPerUnit;
    private BigDecimal totalIngredientCost;

    public ProductionPlanItem() {}

    public ProductionPlanItem(Long menuItemId, String menuItemName, int stallId, String stallName,
                               int predictedQty, int ingredientMaxUnits, int stallCapMax,
                               String ingredientBottleneck, BigDecimal ingredientCostPerUnit) {

        this.menuItemId           = menuItemId;
        this.menuItemName         = menuItemName;
        this.stallId              = stallId;
        this.stallName            = stallName;
        this.predictedQty         = predictedQty;
        this.ingredientMaxUnits   = ingredientMaxUnits;
        this.stallCapMax          = stallCapMax;
        this.ingredientBottleneck = ingredientBottleneck;
        this.ingredientCostPerUnit = ingredientCostPerUnit;

        // Core formula: actual = min(predicted, ingredientMax, stallCap)
        this.actualProduce = Math.min(predictedQty, Math.min(ingredientMaxUnits, stallCapMax));

        // Determine what caused the cap
        if (actualProduce == predictedQty) {
            this.limitingFactor = LimitingFactor.NONE;
        } else if (actualProduce == stallCapMax && stallCapMax <= ingredientMaxUnits) {
            this.limitingFactor = LimitingFactor.STALL_CAPACITY;
        } else {
            this.limitingFactor = LimitingFactor.INGREDIENT;
        }

        // Waste risk based on overproduction vs predicted
        double ratio = predictedQty > 0 ? (double) actualProduce / predictedQty : 1.0;
        this.wasteRisk = ratio > 1.15 ? WasteRisk.HIGH
                       : ratio > 1.05 ? WasteRisk.MEDIUM
                       : WasteRisk.LOW;

        // Total ingredient cost for this production run
        this.totalIngredientCost = ingredientCostPerUnit != null
                ? ingredientCostPerUnit.multiply(BigDecimal.valueOf(actualProduce))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    // Getters
    public Long   getMenuItemId()           { return menuItemId; }
    public String getMenuItemName()         { return menuItemName; }
    public int    getStallId()              { return stallId; }
    public String getStallName()            { return stallName; }
    public int    getPredictedQty()         { return predictedQty; }
    public int    getIngredientMaxUnits()   { return ingredientMaxUnits; }
    public int    getStallCapMax()          { return stallCapMax; }
    public int    getActualProduce()        { return actualProduce; }
    public LimitingFactor getLimitingFactor(){ return limitingFactor; }
    public String getIngredientBottleneck() { return ingredientBottleneck; }
    public WasteRisk getWasteRisk()         { return wasteRisk; }
    public BigDecimal getIngredientCostPerUnit(){ return ingredientCostPerUnit; }
    public BigDecimal getTotalIngredientCost()  { return totalIngredientCost; }

    // Convenience booleans for frontend
    public boolean isFullyConstrained() { return actualProduce < predictedQty; }
    public boolean isIngredientLimited(){ return limitingFactor == LimitingFactor.INGREDIENT; }
    public boolean isCapacityLimited()  { return limitingFactor == LimitingFactor.STALL_CAPACITY; }
}
