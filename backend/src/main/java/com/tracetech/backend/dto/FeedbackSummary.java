package com.tracetech.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response returned after EOD processing — shown on the vendor dashboard.
 * Includes daily MAPE, total inventory deducted, and per-item accuracy breakdown.
 */
public class FeedbackSummary {

    private LocalDate date;
    private int itemsProcessed;
    private BigDecimal dailyMape;           // Mean Absolute Percentage Error for the day
    private BigDecimal inventoryDeducted;   // total units removed from stock today
    private List<ItemAccuracy> itemBreakdown;

    // ── inner DTO ────────────────────────────────────────────────────────────

    public static class ItemAccuracy {
        private Long menuItemId;
        private String menuItemName;
        private int predicted;
        private int actual;
        private BigDecimal errorPct;         // signed: negative = over-predicted
        private BigDecimal absErrorPct;
        private String verdict;              // ACCURATE | OVER_PREDICTED | UNDER_PREDICTED

        public ItemAccuracy(Long menuItemId, String name, int predicted, int actual,
                            BigDecimal errorPct, BigDecimal absErrorPct, BigDecimal accuracyThreshold) {
            this.menuItemId   = menuItemId;
            this.menuItemName = name;
            this.predicted    = predicted;
            this.actual       = actual;
            this.errorPct     = errorPct;
            this.absErrorPct  = absErrorPct;
            // within configured threshold = ACCURATE
            this.verdict = absErrorPct.compareTo(accuracyThreshold) <= 0 ? "ACCURATE"
                         : predicted > actual                             ? "OVER_PREDICTED"
                         :                                                  "UNDER_PREDICTED";
        }

        public Long getMenuItemId()        { return menuItemId; }
        public String getMenuItemName()    { return menuItemName; }
        public int getPredicted()          { return predicted; }
        public int getActual()             { return actual; }
        public BigDecimal getErrorPct()    { return errorPct; }
        public BigDecimal getAbsErrorPct() { return absErrorPct; }
        public String getVerdict()         { return verdict; }
    }

    // ── accessors ────────────────────────────────────────────────────────────

    public LocalDate getDate()                   { return date; }
    public int getItemsProcessed()               { return itemsProcessed; }
    public BigDecimal getDailyMape()             { return dailyMape; }
    public BigDecimal getInventoryDeducted()     { return inventoryDeducted; }
    public List<ItemAccuracy> getItemBreakdown() { return itemBreakdown; }

    public void setDate(LocalDate date)                            { this.date = date; }
    public void setItemsProcessed(int itemsProcessed)              { this.itemsProcessed = itemsProcessed; }
    public void setDailyMape(BigDecimal dailyMape)                 { this.dailyMape = dailyMape; }
    public void setInventoryDeducted(BigDecimal inventoryDeducted) { this.inventoryDeducted = inventoryDeducted; }
    public void setItemBreakdown(List<ItemAccuracy> itemBreakdown) { this.itemBreakdown = itemBreakdown; }
}
