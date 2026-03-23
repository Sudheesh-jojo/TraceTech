package com.tracetech.backend.repository;

import com.tracetech.backend.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByIsActiveTrue();
    List<MenuItem> findByStallId(Integer stallId);
    List<MenuItem> findByCluster(String cluster);
}