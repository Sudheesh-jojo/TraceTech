package com.tracetech.backend.repository;

import com.tracetech.backend.entity.StallCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StallCapacityRepository extends JpaRepository<StallCapacity, Integer> {

    Optional<StallCapacity> findByStallId(Integer stallId);

    // Returns all 4 stalls ordered — used to preload capacity map at plan time
    List<StallCapacity> findAllByOrderByStallIdAsc();

    // Total capacity across all stalls — for dashboard KPI
    @Query("SELECT SUM(s.maxTotalUnitsPerDay) FROM StallCapacity s")
    Integer sumAllCapacity();
}
