package com.tracetech.backend.scheduler;

import com.tracetech.backend.entity.SalesActual;
import com.tracetech.backend.repository.AccuracyLogRepository;
import com.tracetech.backend.repository.SalesActualRepository;
import com.tracetech.backend.service.EndOfDayFeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FeedbackScheduler — optional safety net.
 *
 * If the vendor forgets to call POST /api/feedback/end-of-day,
 * this runs at 23:55 IST and auto-processes the day IF AND ONLY IF:
 *   - SalesActual records exist for today (vendor entered actuals in the Sales Input page)
 *   - No accuracy_log entries exist yet (EOD hasn't been run manually)
 *
 * FIX: Uses SalesActualRepository (where the vendor's manual entries go via the
 * Sales Input page) instead of DailySalesRecordRepository (which is only populated
 * BY the EOD process itself — checking it would always find nothing).
 *
 * Uses @Scheduled — requires @EnableScheduling on BackendApplication.
 */
@Component
public class FeedbackScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedbackScheduler.class);

    private final EndOfDayFeedbackService feedbackService;
    private final SalesActualRepository   salesActualRepo;
    private final AccuracyLogRepository   accuracyRepo;

    public FeedbackScheduler(EndOfDayFeedbackService feedbackService,
                              SalesActualRepository salesActualRepo,
                              AccuracyLogRepository accuracyRepo) {
        this.feedbackService = feedbackService;
        this.salesActualRepo = salesActualRepo;
        this.accuracyRepo    = accuracyRepo;
    }

    @Scheduled(cron = "0 55 23 * * *", zone = "Asia/Kolkata")
    public void autoProcessIfMissed() {
        LocalDate today = LocalDate.now();
        log.info("FeedbackScheduler: checking if EOD feedback needed for {}", today);

        // Guard 1: sales must exist (vendor entered actuals in Sales Input page)
        List<SalesActual> todaySales = salesActualRepo.findBySaleDate(today);
        if (todaySales.isEmpty()) {
            log.info("FeedbackScheduler: no SalesActual records for {} — nothing to process", today);
            return;
        }

        // Guard 2: accuracy_log must NOT already exist (i.e. EOD was already called manually)
        boolean alreadyProcessed = !accuracyRepo
                .findByLogDateOrderByAbsErrorPctDesc(today).isEmpty();
        if (alreadyProcessed) {
            log.info("FeedbackScheduler: {} already fully processed — skipping", today);
            return;
        }

        log.warn("FeedbackScheduler: EOD feedback not submitted for {} — auto-processing", today);

        // Build actuals map from SalesActual records (entered via Sales Input page)
        Map<Long, Integer> actuals = new HashMap<>();
        todaySales.forEach(s ->
                actuals.put(s.getMenuItem().getId(), s.getQtySold()));

        feedbackService.processEndOfDay(today, actuals);
        log.info("FeedbackScheduler: auto-processed {} items for {}", actuals.size(), today);
    }
}
