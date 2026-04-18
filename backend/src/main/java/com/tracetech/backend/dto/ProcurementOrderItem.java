package com.tracetech.backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * One line item in a procurement order.
 * Produced by ProcurementService for every ingredient that has a SHORTFALL or LOW status.
 *
 * urgency:
 *   CRITICAL  — shortfall > 0 AND lead_time >= 2 days (order NOW, may not arrive in time)
 *   HIGH      — shortfall > 0 AND lead_time < 2 days  (order today, arrives tomorrow)
 *   MEDIUM    — status LOW (enough for today, but buffer is thin)
 *   LOW       — proactive reorder (stock healthy, but running below threshold)
 */
public class ProcurementOrderItem {

    public enum Urgency { CRITICAL, HIGH, MEDIUM, LOW }

    private String ingredientName;
    private String unit;
    private String supplierName;

    private BigDecimal currentStock;      // from Inventory
    private BigDecimal neededToday;       // from RecipeService
    private BigDecimal shortfall;         // from StockCheckResult
    private BigDecimal safetyBuffer;      // needed * buffer%

    private BigDecimal leadTimeCover;     // daily_avg * lead_time_days
    private BigDecimal buyQty;            // shortfall + safetyBuffer + leadTimeCover, rounded up to minOrderQty
    private BigDecimal costPerUnit;       // from IngredientSupplier
    private BigDecimal estimatedCost;     // buyQty * costPerUnit

    private int leadTimeDays;
    private LocalDate orderByDate;        // today if CRITICAL/HIGH, tomorrow if MEDIUM
    private LocalDate expectedDelivery;   // orderByDate + leadTimeDays

    private Urgency urgency;

    public ProcurementOrderItem() {}

    // Full constructor — called by ProcurementService
    public ProcurementOrderItem(
            String ingredientName, String unit, String supplierName,
            BigDecimal currentStock, BigDecimal neededToday, BigDecimal shortfall,
            BigDecimal safetyBuffer, BigDecimal leadTimeCover,
            BigDecimal costPerUnit, BigDecimal minOrderQty,
            int leadTimeDays, Urgency urgency, LocalDate today) {

        this.ingredientName = ingredientName;
        this.unit           = unit;
        this.supplierName   = supplierName != null ? supplierName : "Local market";
        this.currentStock   = currentStock.setScale(3, RoundingMode.HALF_UP);
        this.neededToday    = neededToday.setScale(3, RoundingMode.HALF_UP);
        this.shortfall      = shortfall.setScale(3, RoundingMode.HALF_UP);
        this.safetyBuffer   = safetyBuffer.setScale(3, RoundingMode.HALF_UP);
        this.leadTimeCover  = leadTimeCover.setScale(3, RoundingMode.HALF_UP);
        this.costPerUnit    = costPerUnit;
        this.leadTimeDays   = leadTimeDays;
        this.urgency        = urgency;

        // Raw buy quantity = shortfall + safety buffer + lead time cover
        BigDecimal rawBuy = shortfall.add(safetyBuffer).add(leadTimeCover);

        // Enforce minimum order quantity
        this.buyQty = rawBuy.compareTo(minOrderQty) < 0 ? minOrderQty
                    : rawBuy.setScale(3, RoundingMode.CEILING);

        this.estimatedCost = this.buyQty
                .multiply(costPerUnit)
                .setScale(2, RoundingMode.HALF_UP);

        // Order today for CRITICAL/HIGH, can defer to tomorrow for MEDIUM/LOW
        this.orderByDate = (urgency == Urgency.CRITICAL || urgency == Urgency.HIGH)
                ? today : today.plusDays(1);
        this.expectedDelivery = this.orderByDate.plusDays(leadTimeDays);
    }

    // Getters
    public String getIngredientName()     { return ingredientName; }
    public String getUnit()               { return unit; }
    public String getSupplierName()       { return supplierName; }
    public BigDecimal getCurrentStock()   { return currentStock; }
    public BigDecimal getNeededToday()    { return neededToday; }
    public BigDecimal getShortfall()      { return shortfall; }
    public BigDecimal getSafetyBuffer()   { return safetyBuffer; }
    public BigDecimal getLeadTimeCover()  { return leadTimeCover; }
    public BigDecimal getBuyQty()         { return buyQty; }
    public BigDecimal getCostPerUnit()    { return costPerUnit; }
    public BigDecimal getEstimatedCost()  { return estimatedCost; }
    public int getLeadTimeDays()          { return leadTimeDays; }
    public LocalDate getOrderByDate()     { return orderByDate; }
    public LocalDate getExpectedDelivery(){ return expectedDelivery; }
    public Urgency getUrgency()           { return urgency; }
}
