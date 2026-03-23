package com.tracetech.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class SalesSubmitRequest {

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @NotNull(message = "Sale date is required")
    private LocalDate saleDate;

    @Min(value = 0, message = "Quantity sold cannot be negative")
    private Integer qtySold;

    @Min(value = 0, message = "Quantity prepared cannot be negative")
    private Integer qtyPrepared;
}
