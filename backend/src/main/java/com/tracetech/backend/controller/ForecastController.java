package com.tracetech.backend.controller;

import com.tracetech.backend.dto.ForecastResponse;
import com.tracetech.backend.service.ForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    // GET /api/forecast/today
    // Returns today's forecast for all 58 active items
    @GetMapping("/today")
    public ResponseEntity<List<ForecastResponse>> getTodayForecast() {
        return ResponseEntity.ok(forecastService.getTodayForecast());
    }

    // GET /api/forecast/item/{id}
    // Returns 7-day forecast for a single item
    @GetMapping("/item/{id}")
    public ResponseEntity<List<ForecastResponse>> getItemForecast(@PathVariable Long id) {
        return ResponseEntity.ok(forecastService.getItemForecast(id));
    }

    // POST /api/forecast/simulate
    @PostMapping("/simulate")
    public ResponseEntity<List<ForecastResponse>> simulate(
        @RequestBody Map<String, Object> params) {
    return ResponseEntity.ok(forecastService.simulate(params));
    }
    // GET /api/forecast/tomorrow
    @GetMapping("/tomorrow")
    public ResponseEntity<List<ForecastResponse>> getTomorrowForecast() {
        return ResponseEntity.ok(forecastService.getTomorrowForecast());
    }
}
