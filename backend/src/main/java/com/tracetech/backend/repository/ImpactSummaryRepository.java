package com.tracetech.backend.repository;

import com.tracetech.backend.entity.ImpactSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImpactSummaryRepository extends JpaRepository<ImpactSummary, Long> {
    Optional<ImpactSummary> findBySummaryDate(LocalDate date);
    List<ImpactSummary> findBySummaryDateBetweenOrderBySummaryDateAsc(LocalDate start, LocalDate end);
}