package com.tracetech.backend.entity;

import jakarta.persistence.*;

/**
 * Stores the maximum number of units each stall can physically produce per day.
 * One row per stall. stall_id matches menu_items.stall_id (1-4).
 *
 * Stall map (from TraceTech docs):
 *   1 = Snacks stall
 *   2 = Juice & Fruits stall
 *   3 = South Indian stall
 *   4 = Lunch stall
 */
@Entity
@Table(name = "stall_capacity")
public class StallCapacity {

    @Id
    @Column(name = "stall_id")
    private Integer stallId;

    @Column(name = "stall_name", nullable = false, length = 100)
    private String stallName;

    // Maximum total units this stall can produce across ALL its items in one day
    // e.g. Snacks stall can make at most 600 items total (samosas + sandwiches + fries etc.)
    @Column(name = "max_total_units_per_day", nullable = false)
    private int maxTotalUnitsPerDay;

    // Optional per-item cap — max units of any single item regardless of stall total
    // Prevents one item from consuming the entire stall capacity
    // 0 means no per-item cap
    @Column(name = "max_units_per_item", nullable = false)
    private int maxUnitsPerItem = 0;

    // Operating hours (informational — used to calculate throughput)
    @Column(name = "open_hour", nullable = false)
    private int openHour = 8;   // 8 AM

    @Column(name = "close_hour", nullable = false)
    private int closeHour = 20; // 8 PM

    public StallCapacity() {}

    public StallCapacity(Integer stallId, String stallName,
                          int maxTotalUnitsPerDay, int maxUnitsPerItem) {
        this.stallId             = stallId;
        this.stallName           = stallName;
        this.maxTotalUnitsPerDay = maxTotalUnitsPerDay;
        this.maxUnitsPerItem     = maxUnitsPerItem;
    }

    public Integer getStallId()              { return stallId; }
    public String getStallName()             { return stallName; }
    public void setStallName(String v)       { this.stallName = v; }
    public int getMaxTotalUnitsPerDay()      { return maxTotalUnitsPerDay; }
    public void setMaxTotalUnitsPerDay(int v){ this.maxTotalUnitsPerDay = v; }
    public int getMaxUnitsPerItem()          { return maxUnitsPerItem; }
    public void setMaxUnitsPerItem(int v)    { this.maxUnitsPerItem = v; }
    public int getOpenHour()                 { return openHour; }
    public void setOpenHour(int v)           { this.openHour = v; }
    public int getCloseHour()                { return closeHour; }
    public void setCloseHour(int v)          { this.closeHour = v; }
}
