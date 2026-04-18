package com.tracetech.backend.dto;

import java.math.BigDecimal;

// Represents the total amount of one ingredient needed across all items for a given day
public class IngredientRequirement {

    private String ingredientName;
    private BigDecimal totalQtyNeeded;
    private String unit;

    // Per-item breakdown (optional, for detailed view)
    private Long menuItemId;
    private String menuItemName;
    private int predictedQty;
    private BigDecimal qtyPerUnit;
    private BigDecimal lineTotal; // predictedQty * qtyPerUnit

    public IngredientRequirement() {}

    // Constructor for aggregated (daily total) use
    public IngredientRequirement(String ingredientName, BigDecimal totalQtyNeeded, String unit) {
        this.ingredientName = ingredientName;
        this.totalQtyNeeded = totalQtyNeeded;
        this.unit = unit;
    }

    // Constructor for per-item line breakdown
    public IngredientRequirement(Long menuItemId, String menuItemName, int predictedQty,
                                  String ingredientName, BigDecimal qtyPerUnit, String unit) {
        this.menuItemId = menuItemId;
        this.menuItemName = menuItemName;
        this.predictedQty = predictedQty;
        this.ingredientName = ingredientName;
        this.qtyPerUnit = qtyPerUnit;
        this.unit = unit;
        this.lineTotal = qtyPerUnit.multiply(BigDecimal.valueOf(predictedQty));
        this.totalQtyNeeded = this.lineTotal;
    }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public BigDecimal getTotalQtyNeeded() { return totalQtyNeeded; }
    public void setTotalQtyNeeded(BigDecimal totalQtyNeeded) { this.totalQtyNeeded = totalQtyNeeded; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }

    public String getMenuItemName() { return menuItemName; }
    public void setMenuItemName(String menuItemName) { this.menuItemName = menuItemName; }

    public int getPredictedQty() { return predictedQty; }
    public void setPredictedQty(int predictedQty) { this.predictedQty = predictedQty; }

    public BigDecimal getQtyPerUnit() { return qtyPerUnit; }
    public void setQtyPerUnit(BigDecimal qtyPerUnit) { this.qtyPerUnit = qtyPerUnit; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
