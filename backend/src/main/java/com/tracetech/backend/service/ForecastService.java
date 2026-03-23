package com.tracetech.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracetech.backend.dto.ForecastResponse;
import com.tracetech.backend.entity.AcademicCalendar;
import com.tracetech.backend.entity.Forecast;
import com.tracetech.backend.entity.MenuItem;
import com.tracetech.backend.entity.WeatherLog;
import com.tracetech.backend.repository.AcademicCalendarRepository;
import com.tracetech.backend.repository.ForecastRepository;
import com.tracetech.backend.repository.MenuItemRepository;
import com.tracetech.backend.repository.SalesActualRepository;
import com.tracetech.backend.repository.WeatherLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private final ForecastRepository forecastRepository;
    private final MenuItemRepository menuItemRepository;
    private final SalesActualRepository salesActualRepository;
    private final AcademicCalendarRepository academicCalendarRepository;
    private final WeatherLogRepository weatherLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    // ── Get today's forecast for all active items ──────────────
    public List<ForecastResponse> getTodayForecast() {
        LocalDate today = LocalDate.now();
        List<MenuItem> activeItems = menuItemRepository.findByIsActiveTrue();
        List<ForecastResponse> responses = new ArrayList<>();

        for (MenuItem item : activeItems) {
            Optional<Forecast> existing = forecastRepository
                    .findByMenuItem_IdAndForecastDate(item.getId(), today);

            Forecast forecast;
            if (existing.isPresent()) {
                forecast = existing.get();
                log.info("Forecast served from DB for item: {}", item.getName());
            } else {
                forecast = callMlService(item, today);
                forecastRepository.save(forecast);
            }

            responses.add(toResponse(forecast, item));
        }

        return responses;
    }

    // ── Get 7-day forecast for one item ────────────────────────
    public List<ForecastResponse> getItemForecast(Long itemId) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));

        LocalDate today = LocalDate.now();
        List<ForecastResponse> responses = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = today.plusDays(i);
            Optional<Forecast> existing = forecastRepository
                    .findByMenuItem_IdAndForecastDate(itemId, date);

            Forecast forecast;
            if (existing.isPresent()) {
                forecast = existing.get();
            } else {
                forecast = callMlService(item, date);
                forecastRepository.save(forecast);
            }

            responses.add(toResponse(forecast, item));
        }

        return responses;
    }

    // ── Call FastAPI /predict endpoint ─────────────────────────
    private Forecast callMlService(MenuItem item, LocalDate date) {
        try {
            AcademicCalendar cal = academicCalendarRepository.findByCalendarDate(date).orElse(null);
            WeatherLog weather   = weatherLogRepository.findByLogDate(date).orElse(null);

            int prev1day     = getPrevQty(item.getId(), date.minusDays(1));
            int prev2day     = getPrevQty(item.getId(), date.minusDays(2));
            int prev3day     = getPrevQty(item.getId(), date.minusDays(3));
            int prev7day     = getPrevQty(item.getId(), date.minusDays(7));
            double rolling7  = getRollingAvg(item.getId(), date, 7);
            double rolling14 = getRollingAvg(item.getId(), date, 14);

            // day_of_week: XGBoost trained with 0=Mon, 4=Fri, 6=Sun
            int dow = date.getDayOfWeek().getValue() - 1;

            Map<String, Object> request = new HashMap<>();
            request.put("date",                     date.toString());
            request.put("item_id",                  item.getId());
            request.put("item_name",                item.getName());
            request.put("stall_id",                 item.getStallId());
            request.put("cluster",                  item.getCluster());
            request.put("meal_period",              item.getMealPeriod());
            request.put("selling_price",            item.getSellingPrice().doubleValue());
            request.put("ingredient_cost_per_unit", item.getIngredientCostPerUnit().doubleValue());
            request.put("day_of_week",              dow);
            request.put("is_weekend",               (dow >= 5) ? 1 : 0);
            request.put("month",                    date.getMonthValue());
            int weekOfSem = getWeekOfSemester(date);
            request.put("week_of_semester",         weekOfSem);
            request.put("is_first_week",            weekOfSem == 1  ? 1 : 0);
            request.put("is_last_week",             weekOfSem == 20 ? 1 : 0);
            request.put("is_exam_week",             (cal != null && "EXAM".equals(cal.getEventType())) ? 1 : 0);
            request.put("is_holiday",               (cal == null || !cal.getIsCollegeOpen()) ? 1 : 0);
            request.put("is_college_open",          (cal == null || !cal.getIsCollegeOpen()) ? 0 : 1);
            request.put("days_until_exam",          30);
            request.put("weather_condition",        weather != null ? weather.getWeatherCondition() : 0);
            request.put("temperature",              weather != null ? weather.getTemperature().doubleValue() : 34.0);
            request.put("prev_1day_qty",            prev1day);
            request.put("prev_2day_qty",            prev2day);
            request.put("prev_3day_qty",            prev3day);
            request.put("prev_7day_qty",            prev7day);
            request.put("rolling_7day_avg",         rolling7);
            request.put("rolling_14day_avg",        rolling14);

            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> mlResponse = restTemplate.postForObject(
                    mlServiceUrl + "/predict", request, Map.class);

            if (mlResponse == null) throw new RuntimeException("Null response from ML service");

            int predictedQty  = ((Number) mlResponse.get("predicted_qty")).intValue();
            int rangeLow      = ((Number) mlResponse.get("range_low")).intValue();
            int rangeHigh     = ((Number) mlResponse.get("range_high")).intValue();
            boolean anomaly   = (Boolean) mlResponse.get("anomaly_flag");
            String confidence = (String)  mlResponse.get("confidence");

            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) mlResponse.get("top_reasons");
            String reasonsJson = objectMapper.writeValueAsString(reasons);

            log.info("ML prediction for {} on {}: {} units", item.getName(), date, predictedQty);

            return Forecast.builder()
                    .menuItem(item)
                    .forecastDate(date)
                    .predictedQty(predictedQty)
                    .rangeLow(rangeLow)
                    .rangeHigh(rangeHigh)
                    .topReasons(reasonsJson)
                    .anomalyFlag(anomaly)
                    .confidence(confidence)
                    .modelVersion("xgboost_lstm_v1")
                    .build();

        } catch (Exception e) {
            log.error("ML service call failed for {}: {}", item.getName(), e.getMessage());
            return fallbackForecast(item, date);
        }
    }

    // ── Strategy Simulation ────────────────────────────────────
    public List<ForecastResponse> simulate(Map<String, Object> params) {
        List<MenuItem> activeItems = menuItemRepository.findByIsActiveTrue();
        List<ForecastResponse> responses = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (MenuItem item : activeItems) {
            try {
                int prev1day     = getPrevQty(item.getId(), today.minusDays(1));
                int prev2day     = getPrevQty(item.getId(), today.minusDays(2));
                int prev3day     = getPrevQty(item.getId(), today.minusDays(3));
                int prev7day     = getPrevQty(item.getId(), today.minusDays(7));
                double rolling7  = getRollingAvg(item.getId(), today, 7);
                double rolling14 = getRollingAvg(item.getId(), today, 14);

                int simDow = ((Number) params.getOrDefault("day_of_week",
                        today.getDayOfWeek().getValue() - 1)).intValue();

                Map<String, Object> request = new HashMap<>();
                request.put("date",                     today.toString());
                request.put("item_id",                  item.getId());
                request.put("item_name",                item.getName());
                request.put("stall_id",                 item.getStallId());
                request.put("cluster",                  item.getCluster());
                request.put("meal_period",              item.getMealPeriod());
                request.put("selling_price",            item.getSellingPrice().doubleValue());
                request.put("ingredient_cost_per_unit", item.getIngredientCostPerUnit().doubleValue());
                request.put("day_of_week",              simDow);
                request.put("is_weekend",               (simDow >= 5) ? 1 : 0);
                request.put("month",                    today.getMonthValue());
                int weekOfSem = getWeekOfSemester(today);
                request.put("week_of_semester",         weekOfSem);
                request.put("is_first_week",            weekOfSem == 1  ? 1 : 0);
                request.put("is_last_week",             weekOfSem == 20 ? 1 : 0);
                request.put("is_exam_week",             params.getOrDefault("is_exam_week", 0));
                request.put("is_holiday",               params.getOrDefault("is_holiday", 0));
                request.put("is_college_open",          params.getOrDefault("is_holiday", 0).equals(1) ? 0 : 1);
                request.put("days_until_exam",          30);
                request.put("weather_condition",        params.getOrDefault("weather_condition", 0));
                request.put("temperature",              params.getOrDefault("temperature", 34.0));
                request.put("prev_1day_qty",            prev1day);
                request.put("prev_2day_qty",            prev2day);
                request.put("prev_3day_qty",            prev3day);
                request.put("prev_7day_qty",            prev7day);
                request.put("rolling_7day_avg",         rolling7);
                request.put("rolling_14day_avg",        rolling14);

                RestTemplate restTemplate = new RestTemplate();
                @SuppressWarnings("unchecked")
                Map<String, Object> mlResponse = restTemplate.postForObject(
                        mlServiceUrl + "/predict", request, Map.class);

                if (mlResponse == null) throw new RuntimeException("Null response");

                int predictedQty  = ((Number) mlResponse.get("predicted_qty")).intValue();
                int rangeLow      = ((Number) mlResponse.get("range_low")).intValue();
                int rangeHigh     = ((Number) mlResponse.get("range_high")).intValue();
                boolean anomaly   = (Boolean) mlResponse.get("anomaly_flag");
                String confidence = (String)  mlResponse.get("confidence");

                @SuppressWarnings("unchecked")
                List<String> reasons = (List<String>) mlResponse.get("top_reasons");
                String reasonsJson = objectMapper.writeValueAsString(reasons);

                double costIfOver = Math.round(
                        predictedQty * 0.10
                        * item.getIngredientCostPerUnit().doubleValue() * 100.0) / 100.0;

                responses.add(ForecastResponse.builder()
                        .itemId(item.getId())
                        .itemName(item.getName())
                        .cluster(item.getCluster())
                        .mealPeriod(item.getMealPeriod())
                        .stallId(item.getStallId())
                        .predictedQty(predictedQty)
                        .rangeLow(rangeLow)
                        .rangeHigh(rangeHigh)
                        .topReasons(objectMapper.readValue(
                                reasonsJson,
                                new TypeReference<List<String>>() {}))
                        .anomalyFlag(anomaly)
                        .confidence(confidence)
                        .estimatedCostIfOver(costIfOver)
                        .build());

            } catch (Exception e) {
                log.error("Simulate failed for {}: {}", item.getName(), e.getMessage());
            }
        }
        return responses;
    }

    // ── Fallback if FastAPI is down ────────────────────────────
    private Forecast fallbackForecast(MenuItem item, LocalDate date) {
        int base = item.getBaseDailyQty();
        int predicted = (int) Math.round(base * (0.95 + new Random().nextDouble() * 0.1));
        String reasonsJson = "[\"Based on historical average (ML service unavailable)\"]";

        return Forecast.builder()
                .menuItem(item)
                .forecastDate(date)
                .predictedQty(predicted)
                .rangeLow((int)(predicted * 0.88))
                .rangeHigh((int)(predicted * 1.12))
                .topReasons(reasonsJson)
                .anomalyFlag(false)
                .confidence("Low")
                .modelVersion("fallback_v1")
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────
    private int getPrevQty(Long itemId, LocalDate date) {
        return salesActualRepository.findAll().stream()
                .filter(s -> s.getMenuItem().getId().equals(itemId)
                        && s.getSaleDate().equals(date))
                .mapToInt(s -> s.getQtySold() != null ? s.getQtySold() : 0)
                .findFirst().orElse(0);
    }
    public List<ForecastResponse> getTomorrowForecast() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<MenuItem> activeItems = menuItemRepository.findByIsActiveTrue();
        List<ForecastResponse> responses = new ArrayList<>();
        for (MenuItem item : activeItems) {
            Optional<Forecast> existing = forecastRepository
            .findByMenuItem_IdAndForecastDate(item.getId(), tomorrow);
            Forecast forecast;
            if (existing.isPresent()) {
                forecast = existing.get();
            } else {
                forecast = callMlService(item, tomorrow);
                forecastRepository.save(forecast);
            }
            responses.add(toResponse(forecast, item));
        }
        return responses;
    }

    private double getRollingAvg(Long itemId, LocalDate before, int days) {
        List<Integer> qtys = salesActualRepository.findAll().stream()
                .filter(s -> s.getMenuItem().getId().equals(itemId)
                        && s.getSaleDate().isBefore(before)
                        && s.getSaleDate().isAfter(before.minusDays(days + 1)))
                .map(s -> s.getQtySold() != null ? s.getQtySold() : 0)
                .collect(Collectors.toList());

        if (qtys.isEmpty()) return menuItemRepository.findById(itemId)
                .map(m -> (double) m.getBaseDailyQty()).orElse(50.0);

        return qtys.stream().mapToInt(Integer::intValue).average().orElse(50.0);
    }

    private int getWeekOfSemester(LocalDate date) {
    
    LocalDate semStart;
    if (date.getMonthValue() >= 9) {
        semStart = LocalDate.of(date.getYear(), 9, 1);
    } else {
        semStart = LocalDate.of(date.getYear(), 2, 1);
    }
    long days = java.time.temporal.ChronoUnit.DAYS.between(semStart, date);
    return (int) Math.max(1, Math.min(20, (days / 7) + 1));
}

    private ForecastResponse toResponse(Forecast forecast, MenuItem item) {
        List<String> reasons;
        try {
            reasons = objectMapper.readValue(
                    forecast.getTopReasons(),
                    new TypeReference<List<String>>() {});
        } catch (Exception e) {
            reasons = List.of("Based on historical average");
        }

        double costIfOver = Math.round(
                forecast.getPredictedQty() * 0.10
                * item.getIngredientCostPerUnit().doubleValue() * 100.0) / 100.0;

        return ForecastResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .cluster(item.getCluster())
                .mealPeriod(item.getMealPeriod())
                .stallId(item.getStallId())
                .predictedQty(forecast.getPredictedQty())
                .rangeLow(forecast.getRangeLow())
                .rangeHigh(forecast.getRangeHigh())
                .topReasons(reasons)
                .anomalyFlag(forecast.getAnomalyFlag())
                .confidence(forecast.getConfidence())
                .estimatedCostIfOver(costIfOver)
                .build();
    }
}
