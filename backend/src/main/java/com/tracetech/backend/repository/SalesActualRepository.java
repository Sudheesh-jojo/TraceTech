package com.tracetech.backend.repository;

import com.tracetech.backend.entity.SalesActual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesActualRepository extends JpaRepository<SalesActual, Long> {
    List<SalesActual> findBySaleDate(LocalDate date);
    List<SalesActual> findByMenuItem_IdAndSaleDateBetween(Long itemId, LocalDate start, LocalDate end);
    Optional<SalesActual> findByMenuItem_IdAndSaleDate(Long itemId, LocalDate date);
    boolean existsByMenuItem_IdAndSaleDate(Long itemId, LocalDate date);
}