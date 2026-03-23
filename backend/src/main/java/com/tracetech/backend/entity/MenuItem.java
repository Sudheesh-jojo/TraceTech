package com.tracetech.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    // 1=Snacks, 2=Juices, 3=South Indian, 4=Lunch
    @Column(nullable = false)
    private Integer stallId;

    // egg_snacks, samosa, hot_beverages, dosa_batter, etc.
    @Column(nullable = false)
    private String cluster;

    // morning, lunch, afternoon, evening, allday
    @Column(nullable = false)
    private String mealPeriod;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal sellingPrice;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal ingredientCostPerUnit;

    // Rough daily average — used as baseline for synthetic data
    @Column(nullable = false)
    private Integer baseDailyQty;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
