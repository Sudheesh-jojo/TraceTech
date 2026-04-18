package com.tracetech.backend.repository;

import com.tracetech.backend.entity.AccuracyLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccuracyLogRepository extends JpaRepository<AccuracyLog, Long> {

    @EntityGraph(attributePaths = {"menuItem"})
    List<AccuracyLog> findByLogDateOrderByAbsErrorPctDesc(LocalDate logDate);

    Optional<AccuracyLog> findByLogDateAndMenuItemId(LocalDate logDate, Long menuItemId);

    /**
     * Rolling 30-day MAPE per item — what the ML retraining script reads.
     * Returns List<Object[]> where [0] = menuItemId (Long), [1] = avgAbsErrorPct (BigDecimal).
     */
    @Query("""
        SELECT al.menuItem.id, AVG(al.absErrorPct)
        FROM AccuracyLog al
        WHERE al.logDate >= :since
        GROUP BY al.menuItem.id
        ORDER BY AVG(al.absErrorPct) DESC
        """)
    List<Object[]> findRollingMapeByItem(@Param("since") LocalDate since);

    /**
     * Overall daily MAPE time series — drives the accuracy trend chart on the dashboard.
     * Returns List<Object[]> where [0] = logDate, [1] = avgAbsErrorPct.
     */
    @Query("""
        SELECT al.logDate, AVG(al.absErrorPct)
        FROM AccuracyLog al
        WHERE al.logDate >= :since
        GROUP BY al.logDate
        ORDER BY al.logDate DESC
        """)
    List<Object[]> findDailyMapeTimeSeries(@Param("since") LocalDate since);
}
