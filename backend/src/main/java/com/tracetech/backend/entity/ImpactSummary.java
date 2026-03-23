package com.tracetech.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "impact_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate summaryDate;

    // Total items wasted across all menu items
    private Integer totalWasteQty;

    // Waste in rupees
    @Column(precision = 10, scale = 2)
    private BigDecimal totalWasteInr;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalRevenue;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalProfit;

    // % of items where actual was within predicted range
    @Column(precision = 5, scale = 2)
    private BigDecimal forecastAccuracy;

    // Rupees saved vs vendor's old baseline (avg preparation before TraceTech)
    @Column(precision = 10, scale = 2)
    private BigDecimal vsBaselineInr;
}
