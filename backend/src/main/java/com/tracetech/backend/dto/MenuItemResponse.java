// ─── MenuItemResponse.java ───────────────────────────────────
package com.tracetech.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemResponse {
    private Long id;
    private String name;
    private String category;
    private Integer stallId;
    private String cluster;
    private String mealPeriod;
    private Double sellingPrice;
    private Double ingredientCostPerUnit;
    private Integer baseDailyQty;
    private Boolean isActive;
}
