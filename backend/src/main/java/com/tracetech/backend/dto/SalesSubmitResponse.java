package com.tracetech.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesSubmitResponse {
    private Long salesId;
    private String itemName;
    private Integer qtySold;
    private Integer qtyPrepared;
    private Integer qtyWasted;
    private Double wasteCost;       // ₹ wasted
    private Double revenue;         // ₹ earned
    private String message;
}
