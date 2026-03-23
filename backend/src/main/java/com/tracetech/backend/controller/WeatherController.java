package com.tracetech.backend.controller;

import com.tracetech.backend.dto.WeatherResponse;
import com.tracetech.backend.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    // GET /api/weather/today
    // Returns today's weather for Chennai
    // Cached in DB after first call — won't hammer the API
    @GetMapping("/today")
    public ResponseEntity<WeatherResponse> getTodayWeather() {
        return ResponseEntity.ok(weatherService.getTodayWeather());
    }
}
