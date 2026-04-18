package com.tracetech.backend.repository;

import com.tracetech.backend.entity.AcademicCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AcademicCalendarRepository extends JpaRepository<AcademicCalendar, Long> {
    Optional<AcademicCalendar> findByCalendarDate(LocalDate date);

    /** Find the next exam-type event on or after the given date (for days_until_exam). */
    Optional<AcademicCalendar> findFirstByEventTypeAndCalendarDateGreaterThanEqualOrderByCalendarDateAsc(
            String eventType, LocalDate date);
}