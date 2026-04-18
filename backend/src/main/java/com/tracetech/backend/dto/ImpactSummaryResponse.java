package com.tracetech.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImpactSummaryResponse {

    private double totalWasteInr;
    private double totalRevenue;
    private double totalProfit;
    private int    totalWasteQty;
    private double forecastAccuracy;
    private double avgMape;
    private double vsBaselineInr;
    private double wasteReductionPct;

    // FIX 2: New field — replaces the hardcoded "~60%" string in the frontend.
    // Populated from application.properties: tracetech.baseline.forecast-accuracy-pct
    private double baselineAccuracy;

    private String fromDate;
    private String toDate;
    private int    totalDays;
    private double avgDailyWasteInr;
    private double avgDailyRevenue;
    private List<ItemWasteStat> topWastedItems;

    @Data
    @Builder
    public static class ItemWasteStat {
        private String itemName;
        private int    totalWasteQty;
        private double totalWasteCost;
    }
}
