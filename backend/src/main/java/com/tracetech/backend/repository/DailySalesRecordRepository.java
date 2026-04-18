package com.tracetech.backend.repository;

import com.tracetech.backend.entity.DailySalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailySalesRecordRepository extends JpaRepository<DailySalesRecord, Long> {

    List<DailySalesRecord> findBySaleDate(LocalDate saleDate);

    Optional<DailySalesRecord> findBySaleDateAndMenuItemId(LocalDate saleDate, Long menuItemId);

    boolean existsBySaleDate(LocalDate saleDate);

    /**
     * Returns item IDs that have sales records for a date but no accuracy_log entry yet.
     * Used by the scheduler to detect partially-processed days.
     */
    @Query("""
        SELECT dsr.menuItem.id FROM DailySalesRecord dsr
        WHERE dsr.saleDate = :date
          AND dsr.menuItem.id NOT IN (
              SELECT al.menuItem.id FROM AccuracyLog al WHERE al.logDate = :date
          )
        """)
    List<Long> findItemsWithoutAccuracyLog(@Param("date") LocalDate date);
}
