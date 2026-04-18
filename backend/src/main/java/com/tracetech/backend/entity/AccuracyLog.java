package com.tracetech.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "accuracy_log",
       uniqueConstraints = @UniqueConstraint(columnNames = {"log_date", "menu_item_id"}))
public class AccuracyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "predicted_qty", nullable = false)
    private Integer predictedQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    /**
     * Signed percentage error: (actual - predicted) / predicted * 100
     * Negative = over-predicted, positive = under-predicted
     */
    @Column(name = "accuracy_pct", precision = 8, scale = 4)
    private BigDecimal accuracyPct;

    /** Absolute percentage error — used for MAPE calculation */
    @Column(name = "abs_error_pct", precision = 8, scale = 4)
    private BigDecimal absErrorPct;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onPersist() { this.createdAt = LocalDateTime.now(); }

    public Long getId()                { return id; }
    public LocalDate getLogDate()      { return logDate; }
    public MenuItem getMenuItem()      { return menuItem; }
    public Integer getPredictedQty()   { return predictedQty; }
    public Integer getActualQty()      { return actualQty; }
    public BigDecimal getAccuracyPct() { return accuracyPct; }
    public BigDecimal getAbsErrorPct() { return absErrorPct; }
    public LocalDateTime getCreatedAt(){ return createdAt; }

    public void setLogDate(LocalDate logDate)           { this.logDate = logDate; }
    public void setMenuItem(MenuItem menuItem)           { this.menuItem = menuItem; }
    public void setPredictedQty(Integer predictedQty)   { this.predictedQty = predictedQty; }
    public void setActualQty(Integer actualQty)         { this.actualQty = actualQty; }
    public void setAccuracyPct(BigDecimal accuracyPct)  { this.accuracyPct = accuracyPct; }
    public void setAbsErrorPct(BigDecimal absErrorPct)  { this.absErrorPct = absErrorPct; }
}
