package com.tracetech.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetech.backend.dto.WeatherResponse;
import com.tracetech.backend.entity.WeatherLog;
import com.tracetech.backend.repository.WeatherLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private final WeatherLogRepository weatherLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.url}")
    private String apiUrl;

    @Value("${weather.city}")
    private String city;

    // ── Get today's weather (cached or live) ───────────────────
    public WeatherResponse getTodayWeather() {
        LocalDate today = LocalDate.now();

        // Check cache first — only call API once per day
        Optional<WeatherLog> cached = weatherLogRepository.findByLogDate(today);
        if (cached.isPresent()) {
            log.info("Weather served from cache for {}", today);
            return toResponse(cached.get(), true);
        }

        // Fetch live from OpenWeatherMap
        if (apiKey.equals("YOUR_API_KEY_HERE")) {
            log.warn("No API key set — returning mock weather");
            return getMockWeather();
        }

        try {
            String url = apiUrl + "?q=" + city + "&appid=" + apiKey + "&units=metric";
            RestTemplate restTemplate = new RestTemplate();
            String json = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(json);
            double temp = root.path("main").path("temp").asDouble();
            double humidity = root.path("main").path("humidity").asDouble();
            String description = root.path("weather").get(0).path("description").asText();
            String main = root.path("weather").get(0).path("main").asText();

            int condition = mapCondition(main);
            String label = mapLabel(condition);

            WeatherLog log2 = WeatherLog.builder()
                    .logDate(today)
                    .temperature(java.math.BigDecimal.valueOf(temp))
                    .weatherCondition(condition)
                    .weatherConditionLabel(label)
                    .fetchedAt(LocalDateTime.now())
                    .build();

            weatherLogRepository.save(log2);
            log.info("Weather fetched live — {}°C, {}", temp, label);
            return toResponse(log2, false);

        } catch (Exception e) {
            log.error("Weather API call failed: {}", e.getMessage());
            return getMockWeather();
        }
    }

    // ── Map OpenWeatherMap condition to our 0-3 scale ──────────
    private int mapCondition(String main) {
        return switch (main.toLowerCase()) {
            case "clear"         -> 0;
            case "clouds"        -> 1;
            case "rain", "drizzle" -> 2;
            case "thunderstorm", "tornado" -> 3;
            default              -> 1;
        };
    }

    private String mapLabel(int condition) {
        return switch (condition) {
            case 0 -> "Clear";
            case 1 -> "Cloudy";
            case 2 -> "Rainy";
            case 3 -> "Stormy";
            default -> "Cloudy";
        };
    }

    // ── Mock weather for when no API key is set ────────────────
    private WeatherResponse getMockWeather() {
        return WeatherResponse.builder()
                .city(city)
                .temperature(34.0)
                .weatherCondition(0)
                .weatherLabel("Clear")
                .description("clear sky (mock)")
                .humidity(65.0)
                .fetchedAt(LocalDateTime.now().toString())
                .fromCache(false)
                .build();
    }

    private WeatherResponse toResponse(WeatherLog log, boolean fromCache) {
        return WeatherResponse.builder()
                .city(city)
                .temperature(log.getTemperature() != null ?
                        log.getTemperature().doubleValue() : 34.0)
                .weatherCondition(log.getWeatherCondition())
                .weatherLabel(log.getWeatherConditionLabel())
                .description(log.getWeatherConditionLabel())
                .fetchedAt(log.getFetchedAt() != null ?
                        log.getFetchedAt().toString() : LocalDateTime.now().toString())
                .fromCache(fromCache)
                .build();
    }
}
