package com.tracetech.backend.repository;

import com.tracetech.backend.entity.AcademicCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AcademicCalendarRepository extends JpaRepository<AcademicCalendar, Long> {
    Optional<AcademicCalendar> findByCalendarDate(LocalDate date);
}