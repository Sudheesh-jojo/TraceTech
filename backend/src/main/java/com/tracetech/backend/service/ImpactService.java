package com.tracetech.backend.service;

import com.tracetech.backend.dto.ImpactSummaryResponse;
import com.tracetech.backend.entity.Forecast;
import com.tracetech.backend.entity.SalesActual;
import com.tracetech.backend.repository.ForecastRepository;
import com.tracetech.backend.repository.SalesActualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImpactService {

    private final SalesActualRepository salesActualRepository;
    private final ForecastRepository forecastRepository;

    // ── Get impact summary for a date range ────────────────────
    public ImpactSummaryResponse getSummary(LocalDate from, LocalDate to) {

        List<SalesActual> sales = salesActualRepository
                .findByMenuItem_IdAndSaleDateBetween(null, from, to);

        // Use all sales in range
        List<SalesActual> allSales = salesActualRepository.findAll().stream()
                .filter(s -> !s.getSaleDate().isBefore(from) && !s.getSaleDate().isAfter(to))
                .collect(Collectors.toList());

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
                    .fromDate(from.toString())
                    .toDate(to.toString())
                    .totalDays(0)
                    .avgDailyWasteInr(0.0)
                    .avgDailyRevenue(0.0)
                    .topWastedItems(List.of())
                    .build();
        }

        // ── Totals ───────────────────────────────────────────
        double totalWasteInr = allSales.stream()
                .mapToDouble(s -> s.getWasteCost() != null ? s.getWasteCost().doubleValue() : 0)
                .sum();

        double totalRevenue = allSales.stream()
                .mapToDouble(s -> s.getRevenue() != null ? s.getRevenue().doubleValue() : 0)
                .sum();

        int totalWasteQty = allSales.stream()
                .mapToInt(s -> s.getQtyWasted() != null ? s.getQtyWasted() : 0)
                .sum();

        // ── Forecast accuracy ────────────────────────────────
        long withinRange = 0;
        double totalMape = 0;
        int forecastCount = 0;

        for (SalesActual sale : allSales) {
            Optional<Forecast> forecast = forecastRepository
                    .findByMenuItem_IdAndForecastDate(
                            sale.getMenuItem().getId(), sale.getSaleDate());

            if (forecast.isPresent()) {
                Forecast f = forecast.get();
                int actual = sale.getQtySold();
                int predicted = f.getPredictedQty();

                // Within range check
                if (f.getRangeLow() != null && f.getRangeHigh() != null) {
                    if (actual >= f.getRangeLow() && actual <= f.getRangeHigh()) {
                        withinRange++;
                    }
                }

                // MAPE
                if (actual > 0) {
                    totalMape += Math.abs((double)(actual - predicted) / actual) * 100;
                    forecastCount++;
                }
            }
        }

        double forecastAccuracy = allSales.isEmpty() ? 0 :
                (double) withinRange / allSales.size() * 100;

        double avgMape = forecastCount > 0 ? totalMape / forecastCount : 0;

        // ── Baseline comparison ──────────────────────────────
        // Vendor baseline: assumed to prepare 15% more than actual sales
        double baselineWaste = allSales.stream()
                .mapToDouble(s -> {
                    int sold = s.getQtySold();
                    double baselinePrepared = sold * 1.15;
                    double baselineWasted = baselinePrepared - sold;
                    double costPerUnit = s.getMenuItem()
                            .getIngredientCostPerUnit().doubleValue();
                    return baselineWasted * costPerUnit;
                })
                .sum();

        double vsBaselineInr = Math.max(0, baselineWaste - totalWasteInr);
        double wasteReductionPct = baselineWaste > 0 ?
                (vsBaselineInr / baselineWaste) * 100 : 0;

        // ── Date range stats ─────────────────────────────────
        long distinctDays = allSales.stream()
                .map(SalesActual::getSaleDate)
                .distinct().count();

        double avgDailyWaste   = distinctDays > 0 ? totalWasteInr / distinctDays : 0;
        double avgDailyRevenue = distinctDays > 0 ? totalRevenue / distinctDays : 0;

        // Profit estimate (revenue - prep cost)
        double totalPrepCost = allSales.stream()
                .mapToDouble(s -> {
                    int prepared = s.getQtyPrepared() != null ? s.getQtyPrepared() : s.getQtySold();
                    return prepared * s.getMenuItem().getIngredientCostPerUnit().doubleValue();
                })
                .sum();
        double totalProfit = totalRevenue - totalPrepCost;

        // ── Top wasted items ─────────────────────────────────
        Map<String, double[]> wasteByItem = new LinkedHashMap<>();
        for (SalesActual sale : allSales) {
            String name = sale.getMenuItem().getName();
            int wasted = sale.getQtyWasted() != null ? sale.getQtyWasted() : 0;
            double cost = sale.getWasteCost() != null ? sale.getWasteCost().doubleValue() : 0;
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
                .totalRevenue(Math.round(totalRevenue * 100.0) / 100.0)
                .totalProfit(Math.round(totalProfit * 100.0) / 100.0)
                .totalWasteQty(totalWasteQty)
                .forecastAccuracy(Math.round(forecastAccuracy * 100.0) / 100.0)
                .avgMape(Math.round(avgMape * 100.0) / 100.0)
                .vsBaselineInr(Math.round(vsBaselineInr * 100.0) / 100.0)
                .wasteReductionPct(Math.round(wasteReductionPct * 100.0) / 100.0)
                .fromDate(from.toString())
                .toDate(to.toString())
                .totalDays((int) distinctDays)
                .avgDailyWasteInr(Math.round(avgDailyWaste * 100.0) / 100.0)
                .avgDailyRevenue(Math.round(avgDailyRevenue * 100.0) / 100.0)
                .topWastedItems(topWasted)
                .build();
    }
}
