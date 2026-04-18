package com.tracetech.backend.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents the stock check result for ONE ingredient.
 * StockCheckService produces a list of these — one per ingredient needed today.
 *
 * status:
 *   OK         — current stock covers today's need + safety buffer
 *   LOW        — current stock covers today's need but not the buffer
 *   SHORTFALL  — current stock cannot cover today's need at all
 *   MISSING    — ingredient has no inventory row yet (needs to be added)
 */
public class StockCheckResult {

    public enum StockStatus { OK, LOW, SHORTFALL, MISSING }

    private String ingredientName;
    private String unit;

    private BigDecimal needed;          // from RecipeService calculation
    private BigDecimal available;       // from Inventory table (0 if MISSING)
    private BigDecimal safetyBuffer;    // needed * safetyBufferPct / 100
    private BigDecimal shortfall;       // max(0, needed + buffer - available)
    private BigDecimal surplus;         // max(0, available - needed - buffer)

    private StockStatus status;

    public StockCheckResult() {}

    // Constructor used by StockCheckService
    public StockCheckResult(String ingredientName, String unit,
                             BigDecimal needed, BigDecimal available,
                             BigDecimal safetyBufferPct) {
        this.ingredientName = ingredientName;
        this.unit = unit;
        this.needed = needed.setScale(3, RoundingMode.HALF_UP);
        this.available = available.setScale(3, RoundingMode.HALF_UP);

        // safety buffer = needed * bufferPct%
        this.safetyBuffer = needed
                .multiply(safetyBufferPct)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);

        BigDecimal totalRequired = needed.add(safetyBuffer);

        if (available.compareTo(totalRequired) >= 0) {
            this.status = StockStatus.OK;
            this.shortfall = BigDecimal.ZERO;
            this.surplus = available.subtract(totalRequired).setScale(3, RoundingMode.HALF_UP);
        } else if (available.compareTo(needed) >= 0) {
            this.status = StockStatus.LOW;
            this.shortfall = totalRequired.subtract(available).setScale(3, RoundingMode.HALF_UP);
            this.surplus = BigDecimal.ZERO;
        } else {
            this.status = StockStatus.SHORTFALL;
            this.shortfall = totalRequired.subtract(available).setScale(3, RoundingMode.HALF_UP);
            this.surplus = BigDecimal.ZERO;
        }
    }

    // Constructor for MISSING status
    public static StockCheckResult missing(String ingredientName, BigDecimal needed) {
        StockCheckResult r = new StockCheckResult();
        r.ingredientName = ingredientName;
        r.needed = needed.setScale(3, RoundingMode.HALF_UP);
        r.available = BigDecimal.ZERO;
        r.safetyBuffer = BigDecimal.ZERO;
        r.shortfall = needed.setScale(3, RoundingMode.HALF_UP);
        r.surplus = BigDecimal.ZERO;
        r.status = StockStatus.MISSING;
        return r;
    }

    public boolean isOk()       { return status == StockStatus.OK; }
    public boolean isLow()      { return status == StockStatus.LOW; }
    public boolean isShortfall(){ return status == StockStatus.SHORTFALL || status == StockStatus.MISSING; }

    // Getters
    public String getIngredientName()  { return ingredientName; }
    public String getUnit()            { return unit; }
    public BigDecimal getNeeded()      { return needed; }
    public BigDecimal getAvailable()   { return available; }
    public BigDecimal getSafetyBuffer(){ return safetyBuffer; }
    public BigDecimal getShortfall()   { return shortfall; }
    public BigDecimal getSurplus()     { return surplus; }
    public StockStatus getStatus()     { return status; }
    public void setUnit(String unit)   { this.unit = unit; }
}
