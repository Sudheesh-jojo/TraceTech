package com.tracetech.backend.controller;

import com.tracetech.backend.dto.SalesSubmitRequest;
import com.tracetech.backend.dto.SalesSubmitResponse;
import com.tracetech.backend.service.SalesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    // POST /api/sales/submit
    // Submit actual sales for one item
    @PostMapping("/submit")
    public ResponseEntity<SalesSubmitResponse> submitSales(
            @Valid @RequestBody SalesSubmitRequest request) {
        return ResponseEntity.ok(salesService.submitSales(request));
    }

    // POST /api/sales/submit/bulk
    // Submit actual sales for multiple items at once
    @PostMapping("/submit/bulk")
    public ResponseEntity<List<SalesSubmitResponse>> submitBulkSales(
            @Valid @RequestBody List<SalesSubmitRequest> requests) {
        return ResponseEntity.ok(salesService.submitBulkSales(requests));
    }
}
