// ─── ForecastResponse.java ───────────────────────────────────
package com.tracetech.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResponse {
    private Long itemId;
    private String itemName;
    private String cluster;
    private String mealPeriod;
    private Integer stallId;
    private Integer predictedQty;
    private Integer rangeLow;
    private Integer rangeHigh;
    private List<String> topReasons;
    private Boolean anomalyFlag;
    private String confidence;         // High, Medium, Low
    private Double estimatedCostIfOver; // rupees wasted if over-prepared
}
