package com.tracetech.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Stores supplier/cost info for each ingredient.
 * ingredient_name matches exactly the ingredient_name in recipes + inventory tables.
 * One row per ingredient — one supplier per ingredient (keep it simple for v1).
 */
@Entity
@Table(name = "ingredient_suppliers")
public class IngredientSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Join key — must match ingredient_name in recipes and inventory exactly
    @Column(name = "ingredient_name", nullable = false, unique = true, length = 100)
    private String ingredientName;

    // Cost per unit (in rupees). Unit matches inventory.unit for this ingredient.
    @Column(name = "cost_per_unit", nullable = false, precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    // How many days after ordering does delivery arrive?
    // 0 = same day (local market), 1 = next day, 2 = 2 days, etc.
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 1;

    // Supplier name — purely informational for the vendor
    @Column(name = "supplier_name", length = 100)
    private String supplierName;

    // Minimum order quantity — some suppliers won't sell below a threshold
    @Column(name = "min_order_qty", precision = 10, scale = 4)
    private BigDecimal minOrderQty = BigDecimal.ONE;

    public IngredientSupplier() {}

    public IngredientSupplier(String ingredientName, BigDecimal costPerUnit,
                               int leadTimeDays, String supplierName) {
        this.ingredientName = ingredientName;
        this.costPerUnit    = costPerUnit;
        this.leadTimeDays   = leadTimeDays;
        this.supplierName   = supplierName;
    }

    public Long getId()                      { return id; }
    public String getIngredientName()        { return ingredientName; }
    public void setIngredientName(String v)  { this.ingredientName = v; }
    public BigDecimal getCostPerUnit()       { return costPerUnit; }
    public void setCostPerUnit(BigDecimal v) { this.costPerUnit = v; }
    public int getLeadTimeDays()             { return leadTimeDays; }
    public void setLeadTimeDays(int v)       { this.leadTimeDays = v; }
    public String getSupplierName()          { return supplierName; }
    public void setSupplierName(String v)    { this.supplierName = v; }
    public BigDecimal getMinOrderQty()       { return minOrderQty; }
    public void setMinOrderQty(BigDecimal v) { this.minOrderQty = v; }
}
