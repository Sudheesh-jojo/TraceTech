package com.tracetech.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate logDate;

    // 0=clear, 1=cloudy, 2=rainy, 3=hot
    @Column(name = "weather_condition", nullable = false)
    private Integer weatherCondition;

    // clear, cloudy, rainy, hot
    @Column(name = "weather_condition_label", nullable = false)
    private String weatherConditionLabel;

    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    @Builder.Default
    private LocalDateTime fetchedAt = LocalDateTime.now();
}