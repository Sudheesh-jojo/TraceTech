package com.tracetech.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "forecasts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Forecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private LocalDate forecastDate;

    @Column(nullable = false)
    private Integer predictedQty;

    private Integer rangeLow;
    private Integer rangeHigh;

    // JSON string: ["Exam week", "Friday", "Recent trend up"]
    @Column(columnDefinition = "TEXT")
    private String topReasons;

    @Column(nullable = false)
    @Builder.Default
    private Boolean anomalyFlag = false;

    // High, Medium, Low
    private String confidence;

    // e.g. "ensemble_v1"
    private String modelVersion;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
