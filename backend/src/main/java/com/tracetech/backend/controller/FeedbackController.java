package com.tracetech.backend.controller;

import com.tracetech.backend.dto.FeedbackRequest;
import com.tracetech.backend.dto.FeedbackSummary;
import com.tracetech.backend.dto.FeedbackSummary.ItemAccuracy;
import com.tracetech.backend.entity.AccuracyLog;
import com.tracetech.backend.repository.AccuracyLogRepository;
import com.tracetech.backend.service.EndOfDayFeedbackService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final EndOfDayFeedbackService feedbackService;
    private final AccuracyLogRepository   accuracyRepo;

    @Value("${tracetech.accuracy.threshold-pct}")
    private BigDecimal accuracyThresholdPct;

    public FeedbackController(EndOfDayFeedbackService feedbackService,
                               AccuracyLogRepository accuracyRepo) {
        this.feedbackService = feedbackService;
        this.accuracyRepo    = accuracyRepo;
    }

    /**
     * POST /api/feedback/end-of-day
     *
     * The vendor calls this once the day's sales are entered.
     * Triggers all sub-steps in one transaction:
     *   sales record → accuracy log → inventory deduction
     *
     * Body example:
     * {
     *   "date": "2026-04-14",
     *   "actuals": { "9": 112, "14": 87, "22": 61 }
     * }
     */
    @PostMapping("/end-of-day")
    public ResponseEntity<FeedbackSummary> submitEndOfDay(@RequestBody FeedbackRequest request) {
        LocalDate date = request.getDate() != null ? request.getDate() : LocalDate.now();
        FeedbackSummary summary = feedbackService.processEndOfDay(date, request.getActuals());
        return ResponseEntity.ok(summary);
    }

    /**
     * GET /api/feedback/accuracy?date=2026-04-14
     *
     * Returns per-item accuracy breakdown for a given day, sorted worst-first.
     * Maps AccuracyLog entities to ItemAccuracy DTOs to avoid LazyInitializationException
     * (AccuracyLog.menuItem is LAZY and open-in-view=false) and to include
     * menuItemName + verdict fields that the frontend needs.
     */
    @GetMapping("/accuracy")
    public ResponseEntity<List<ItemAccuracy>> getDailyAccuracy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<AccuracyLog> logs = accuracyRepo.findByLogDateOrderByAbsErrorPctDesc(date);

        List<ItemAccuracy> dtos = logs.stream()
                .map(al -> new ItemAccuracy(
                        al.getMenuItem().getId(),
                        al.getMenuItem().getName(),
                        al.getPredictedQty(),
                        al.getActualQty(),
                        al.getAccuracyPct(),
                        al.getAbsErrorPct(),
                        accuracyThresholdPct))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/feedback/mape/rolling
     *
     * Returns rolling 30-day MAPE per item as { menuItemId → mape }.
     * The Python ML retraining script calls this to find which items
     * have drifted and need extra XGBoost/LSTM training cycles.
     */
    @GetMapping("/mape/rolling")
    public ResponseEntity<Map<Long, BigDecimal>> getRollingMape() {
        return ResponseEntity.ok(feedbackService.getRollingMape());
    }

    /**
     * GET /api/feedback/mape/trend?since=2026-03-01
     *
     * Returns daily overall MAPE time series — drives the accuracy trend chart.
     * since defaults to 30 days ago if omitted.
     * Returns List<[logDate, avgAbsErrorPct]>.
     */
    @GetMapping("/mape/trend")
    public ResponseEntity<List<Object[]>> getMapeTrend(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        LocalDate from = since != null ? since : LocalDate.now().minusDays(30);
        return ResponseEntity.ok(accuracyRepo.findDailyMapeTimeSeries(from));
    }
}
