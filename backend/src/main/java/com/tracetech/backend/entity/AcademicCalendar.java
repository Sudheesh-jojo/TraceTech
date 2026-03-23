package com.tracetech.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "academic_calendar")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate calendarDate;

    // regular, exam, holiday, first_week, last_week, weekend
    @Column(nullable = false)
    private String eventType;

    private String eventName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCollegeOpen = true;
}
