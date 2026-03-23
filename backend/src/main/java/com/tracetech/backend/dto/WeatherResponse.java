// ─── WeatherResponse.java ────────────────────────────────────
package com.tracetech.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherResponse {
    private String city;
    private Double temperature;        // celsius
    private Integer weatherCondition;  // 0=clear, 1=cloudy, 2=rainy, 3=stormy
    private String weatherLabel;       // "Clear", "Cloudy", "Rainy", "Stormy"
    private String description;        // raw from OpenWeatherMap
    private Double humidity;
    private String fetchedAt;
    private Boolean fromCache;         // true if pulled from DB, false if live
}
