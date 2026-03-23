package com.tracetech.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactSummaryResponse {
    // Overall totals
    private Double totalWasteInr;        // total ₹ wasted
    private Double totalRevenue;         // total ₹ earned
    private Double totalProfit;          // revenue - prep cost
    private Integer totalWasteQty;       // total units wasted

    // Forecast accuracy
    private Double forecastAccuracy;     // % of items within predicted range
    private Double avgMape;              // mean absolute % error

    // The big number — what we saved vs vendor's old baseline
    private Double vsBaselineInr;        // ₹ saved by using TraceTech
    private Double wasteReductionPct;    // % reduction in waste

    // Date range
    private String fromDate;
    private String toDate;
    private Integer totalDays;

    // Per day averages
    private Double avgDailyWasteInr;
    private Double avgDailyRevenue;

    // Top wasted items
    private java.util.List<ItemWasteStat> topWastedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemWasteStat {
        private String itemName;
        private Integer totalWasteQty;
        private Double totalWasteCost;
    }
}
