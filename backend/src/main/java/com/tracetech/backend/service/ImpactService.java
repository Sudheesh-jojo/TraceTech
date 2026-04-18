package com.tracetech.backend.service;
 
import com.tracetech.backend.dto.ImpactSummaryResponse;
import com.tracetech.backend.entity.Forecast;
import com.tracetech.backend.entity.SalesActual;
import com.tracetech.backend.repository.ForecastRepository;
import com.tracetech.backend.repository.SalesActualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
 
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
 
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ImpactService {
 
    private final SalesActualRepository salesActualRepository;
    private final ForecastRepository forecastRepository;

    // FIX 3: Baseline over-prep % is now configurable via application.properties
    // instead of being hardcoded as 0.15 everywhere.
    // Set   tracetech.baseline.overprep-pct=0.15   in application.properties.
    // This means you can tune it per-deployment without touching source code.
    @Value("${tracetech.baseline.overprep-pct:0.15}")
    private double baselineOverprepPct;

    // FIX 2: Baseline forecast accuracy % (what accuracy was before TraceTech).
    // Set   tracetech.baseline.forecast-accuracy-pct=60.0   in application.properties.
    // Previously this was the hardcoded string "~60%" in the frontend.
    @Value("${tracetech.baseline.forecast-accuracy-pct:60.0}")
    private double baselineForecastAccuracyPct;

    // ── Helper: fetch all sales in date range ──────────────────
    private List<SalesActual> getSalesInRange(LocalDate from, LocalDate to) {
        return salesActualRepository.findAll().stream()
                .filter(s -> !s.getSaleDate().isBefore(from) && !s.getSaleDate().isAfter(to))
                .collect(Collectors.toList());
    }
 
    // ── Get impact summary for a date range ────────────────────
    public ImpactSummaryResponse getSummary(LocalDate from, LocalDate to) {
 
        List<SalesActual> allSales = getSalesInRange(from, to);
 
        if (allSales.isEmpty()) {
            return ImpactSummaryResponse.builder()
                    .totalWasteInr(0.0)
                    .totalRevenue(0.0)
                    .totalProfit(0.0)
                    .totalWasteQty(0)
                    .forecastAccuracy(0.0)
                    .avgMape(0.0)
                    .vsBaselineInr(0.0)
                    .wasteReductionPct(0.0)
                    // FIX 2: include configurable baseline accuracy in empty response
                    .baselineAccuracy(Math.round(baselineForecastAccuracyPct * 100.0) / 100.0)
                    .fromDate(from.toString())
                    .toDate(to.toString())
                    .totalDays(0)
                    .avgDailyWasteInr(0.0)
                    .avgDailyRevenue(0.0)
                    .topWastedItems(List.of())
                    .build();
        }
 
        // ── Totals ────────────────────────────────────────────
        double totalWasteInr = allSales.stream()
                .mapToDouble(s -> s.getWasteCost() != null ? s.getWasteCost().doubleValue() : 0)
                .sum();
 
        double totalRevenue = allSales.stream()
                .mapToDouble(s -> s.getRevenue() != null ? s.getRevenue().doubleValue() : 0)
                .sum();
 
        int totalWasteQty = allSales.stream()
                .mapToInt(s -> s.getQtyWasted() != null ? s.getQtyWasted() : 0)
                .sum();
 
        // ── Forecast accuracy ─────────────────────────────────
        long withinTolerance = 0;
        double totalMape = 0;
        int forecastCount = 0;

        for (SalesActual sale : allSales) {
            Optional<Forecast> forecast = forecastRepository
                    .findByMenuItem_IdAndForecastDate(
                            sale.getMenuItem().getId(), sale.getSaleDate());

            if (forecast.isPresent()) {
                Forecast f = forecast.get();
                int actual    = sale.getQtySold();
                int predicted = f.getPredictedQty();

                if (actual > 0) {
                    forecastCount++;

                    // MAPE
                    totalMape += Math.abs((double)(actual - predicted) / actual) * 100;

                    // Tolerance-based accuracy (±5% or min 2 units)
                    double tolerance = Math.max(2, actual * 0.05);
                    if (Math.abs(actual - predicted) <= tolerance) {
                        withinTolerance++;
                    }
                }
            }
        }

        double forecastAccuracy = forecastCount > 0
            ? (double) withinTolerance / forecastCount * 100
            : 0;

        double avgMape = forecastCount > 0
            ? totalMape / forecastCount
            : 0;

        // ── Baseline comparison ───────────────────────────────
        // FIX 3: Uses @Value-injected baselineOverprepPct instead of hardcoded 0.15.
        // Configure via: tracetech.baseline.overprep-pct=0.15 in application.properties
        double baselineWaste = allSales.stream()
                .mapToDouble(s -> {
                    int sold = s.getQtySold();
                    double baselineWasted = sold * baselineOverprepPct;
                    double costPerUnit = s.getMenuItem().getIngredientCostPerUnit().doubleValue();
                    return baselineWasted * costPerUnit;
                })
                .sum();
 
        double vsBaselineInr     = Math.max(0, baselineWaste - totalWasteInr);
        double wasteReductionPct = baselineWaste > 0 ? (vsBaselineInr / baselineWaste) * 100 : 0;
 
        // ── Date range stats ──────────────────────────────────
        long distinctDays = allSales.stream()
                .map(SalesActual::getSaleDate)
                .distinct().count();
 
        double avgDailyWaste   = distinctDays > 0 ? totalWasteInr / distinctDays : 0;
        double avgDailyRevenue = distinctDays > 0 ? totalRevenue  / distinctDays : 0;
 
        // ── Profit (revenue - ingredient prep cost) ───────────
        double totalPrepCost = allSales.stream()
                .mapToDouble(s -> {
                    int prepared = s.getQtyPrepared() != null ? s.getQtyPrepared() : s.getQtySold();
                    return prepared * s.getMenuItem().getIngredientCostPerUnit().doubleValue();
                })
                .sum();
        double totalProfit = totalRevenue - totalPrepCost;
 
        // ── Top wasted items ──────────────────────────────────
        Map<String, double[]> wasteByItem = new LinkedHashMap<>();
        for (SalesActual sale : allSales) {
            String name   = sale.getMenuItem().getName();
            int    wasted = sale.getQtyWasted() != null ? sale.getQtyWasted() : 0;
            double cost   = sale.getWasteCost()  != null ? sale.getWasteCost().doubleValue() : 0;
            wasteByItem.merge(name, new double[]{wasted, cost},
                    (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
        }
 
        List<ImpactSummaryResponse.ItemWasteStat> topWasted = wasteByItem.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .limit(5)
                .map(e -> ImpactSummaryResponse.ItemWasteStat.builder()
                        .itemName(e.getKey())
                        .totalWasteQty((int) e.getValue()[0])
                        .totalWasteCost(Math.round(e.getValue()[1] * 100.0) / 100.0)
                        .build())
                .collect(Collectors.toList());
 
        return ImpactSummaryResponse.builder()
                .totalWasteInr(Math.round(totalWasteInr * 100.0) / 100.0)
                .totalRevenue(Math.round(totalRevenue   * 100.0) / 100.0)
                .totalProfit(Math.round(totalProfit     * 100.0) / 100.0)
                .totalWasteQty(totalWasteQty)
                .forecastAccuracy(Math.round(forecastAccuracy   * 100.0) / 100.0)
                .avgMape(Math.round(avgMape                     * 100.0) / 100.0)
                .vsBaselineInr(Math.round(vsBaselineInr         * 100.0) / 100.0)
                .wasteReductionPct(Math.round(wasteReductionPct * 100.0) / 100.0)
                // FIX 2: send baseline accuracy to frontend so it's never hardcoded
                .baselineAccuracy(Math.round(baselineForecastAccuracyPct * 100.0) / 100.0)
                .fromDate(from.toString())
                .toDate(to.toString())
                .totalDays((int) distinctDays)
                .avgDailyWasteInr(Math.round(avgDailyWaste    * 100.0) / 100.0)
                .avgDailyRevenue(Math.round(avgDailyRevenue   * 100.0) / 100.0)
                .topWastedItems(topWasted)
                .build();
    }
 
    // ── Get daily waste breakdown for chart ────────────────────
    public List<Map<String, Object>> getDailyWaste(LocalDate from, LocalDate to) {
 
        List<SalesActual> allSales = getSalesInRange(from, to);
 
        Map<LocalDate, double[]> byDate = new LinkedHashMap<>();
        LocalDate cur = from;
        while (!cur.isAfter(to)) {
            byDate.put(cur, new double[]{0.0, 0.0});
            cur = cur.plusDays(1);
        }
 
        // FIX 3: Uses configurable baselineOverprepPct instead of hardcoded 0.15
        for (SalesActual s : allSales) {
            double waste = s.getWasteCost() != null ? s.getWasteCost().doubleValue() : 0;
            double baselineWaste = s.getQtySold() * baselineOverprepPct
                    * s.getMenuItem().getIngredientCostPerUnit().doubleValue();
 
            byDate.merge(s.getSaleDate(),
                    new double[]{waste, baselineWaste},
                    (a, b) -> new double[]{a[0] + b[0], a[1] + b[1]});
        }
 
        List<Map<String, Object>> result = new ArrayList<>();
        byDate.forEach((date, vals) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date",     date.toString());
            row.put("waste",    Math.round(vals[0] * 100.0) / 100.0);
            row.put("baseline", Math.round(vals[1] * 100.0) / 100.0);
            result.add(row);
        });
 
        return result;
    }
}
