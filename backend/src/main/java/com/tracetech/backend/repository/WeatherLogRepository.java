package com.tracetech.backend.repository;

import com.tracetech.backend.entity.WeatherLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface WeatherLogRepository extends JpaRepository<WeatherLog, Long> {
    Optional<WeatherLog> findByLogDate(LocalDate logDate);
}
