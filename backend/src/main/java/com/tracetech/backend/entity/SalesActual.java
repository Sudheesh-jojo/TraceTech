package com.tracetech.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_actuals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesActual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false)
    private Integer qtySold;

    // How much was actually prepared that day
    private Integer qtyPrepared;

    // qtyPrepared - qtySold
    private Integer qtyWasted;

    @Column(precision = 10, scale = 2)
    private BigDecimal revenue;

    @Column(precision = 10, scale = 2)
    private BigDecimal wasteCost;

    // When the vendor submitted this entry
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}
