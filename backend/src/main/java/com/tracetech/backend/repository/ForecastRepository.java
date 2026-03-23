package com.tracetech.backend.repository;

import com.tracetech.backend.entity.Forecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForecastRepository extends JpaRepository<Forecast, Long> {
    List<Forecast> findByForecastDate(LocalDate date);
    Optional<Forecast> findByMenuItem_IdAndForecastDate(Long itemId, LocalDate date);
    List<Forecast> findByMenuItem_IdAndForecastDateBetween(Long itemId, LocalDate start, LocalDate end);
    boolean existsByMenuItem_IdAndForecastDate(Long itemId, LocalDate date);
}