package com.tracetech.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Matches ingredient_name in recipes table exactly — this is the join key
    @Column(name = "ingredient_name", nullable = false, unique = true, length = 100)
    private String ingredientName;

    @Column(name = "current_qty", nullable = false, precision = 10, scale = 4)
    private BigDecimal currentQty;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    // Safety buffer: minimum stock to always keep on hand regardless of demand
    // Default 10% — vendor can adjust per ingredient
    @Column(name = "safety_buffer_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal safetyBufferPct = new BigDecimal("10.00");

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Inventory() {}

    public Inventory(String ingredientName, BigDecimal currentQty, String unit) {
        this.ingredientName = ingredientName;
        this.currentQty = currentQty;
        this.unit = unit;
    }

    public Long getId() { return id; }
    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }
    public BigDecimal getCurrentQty() { return currentQty; }
    public void setCurrentQty(BigDecimal currentQty) { this.currentQty = currentQty; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getSafetyBufferPct() { return safetyBufferPct; }
    public void setSafetyBufferPct(BigDecimal safetyBufferPct) { this.safetyBufferPct = safetyBufferPct; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
