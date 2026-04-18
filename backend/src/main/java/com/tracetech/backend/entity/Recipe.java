package com.tracetech.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;

    @Column(name = "qty_per_unit", nullable = false, precision = 10, scale = 4)
    private BigDecimal qtyPerUnit;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    public Recipe() {}

    public Recipe(MenuItem menuItem, String ingredientName, BigDecimal qtyPerUnit, String unit) {
        this.menuItem = menuItem;
        this.ingredientName = ingredientName;
        this.qtyPerUnit = qtyPerUnit;
        this.unit = unit;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MenuItem getMenuItem() { return menuItem; }
    public void setMenuItem(MenuItem menuItem) { this.menuItem = menuItem; }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public BigDecimal getQtyPerUnit() { return qtyPerUnit; }
    public void setQtyPerUnit(BigDecimal qtyPerUnit) { this.qtyPerUnit = qtyPerUnit; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
