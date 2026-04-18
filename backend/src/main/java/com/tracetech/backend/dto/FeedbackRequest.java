package com.tracetech.backend.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * Payload the vendor sends when closing out a day.
 *
 * actuals: { menuItemId → actualQtySold }
 * date:    the sale date (defaults to today if omitted)
 *
 * Example JSON:
 * {
 *   "date": "2026-04-14",
 *   "actuals": {
 *     "9":  112,
 *     "14":  87,
 *     "22":  61
 *   }
 * }
 */
public class FeedbackRequest {

    private LocalDate date;
    private Map<Long, Integer> actuals;

    public LocalDate getDate()             { return date; }
    public Map<Long, Integer> getActuals() { return actuals; }

    public void setDate(LocalDate date)                { this.date = date; }
    public void setActuals(Map<Long, Integer> actuals) { this.actuals = actuals; }
}
