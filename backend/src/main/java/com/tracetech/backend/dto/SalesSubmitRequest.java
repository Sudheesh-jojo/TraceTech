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

    @NotNull(message = "Quantity sold is required")
    @Min(value = 0, message = "Quantity sold cannot be negative")
    private Integer qtySold;

    @NotNull(message = "Quantity prepared is required")
    @Min(value = 0, message = "Quantity prepared cannot be negative")
    private Integer qtyPrepared;
}
