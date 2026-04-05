package com.tracetech.backend.controller;

import com.tracetech.backend.dto.ImpactSummaryResponse;
import com.tracetech.backend.service.ImpactService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/impact")
@RequiredArgsConstructor
public class ImpactController {

    private final ImpactService impactService;

    // GET /api/impact/summary?from=2026-03-01&to=2026-03-12
    @GetMapping("/summary")
    public ResponseEntity<ImpactSummaryResponse> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null)   to   = LocalDate.now();

        return ResponseEntity.ok(impactService.getSummary(from, to));
    }

    // GET /api/impact/daily?from=2026-04-01&to=2026-04-05
    @GetMapping("/daily")
    public ResponseEntity<List<Map<String, Object>>> getDailyWaste(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null)   to   = LocalDate.now();

        return ResponseEntity.ok(impactService.getDailyWaste(from, to));
    }
}