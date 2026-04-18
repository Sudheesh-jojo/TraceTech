package com.tracetech.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_sales_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sale_date", "menu_item_id"}))
public class DailySalesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "predicted_qty", nullable = false)
    private Integer predictedQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onPersist() {
        this.recordedAt = LocalDateTime.now();
    }

    public Long getId()                  { return id; }
    public LocalDate getSaleDate()       { return saleDate; }
    public MenuItem getMenuItem()        { return menuItem; }
    public Integer getPredictedQty()     { return predictedQty; }
    public Integer getActualQty()        { return actualQty; }
    public LocalDateTime getRecordedAt() { return recordedAt; }

    public void setSaleDate(LocalDate saleDate)       { this.saleDate = saleDate; }
    public void setMenuItem(MenuItem menuItem)         { this.menuItem = menuItem; }
    public void setPredictedQty(Integer predictedQty) { this.predictedQty = predictedQty; }
    public void setActualQty(Integer actualQty)       { this.actualQty = actualQty; }
}
