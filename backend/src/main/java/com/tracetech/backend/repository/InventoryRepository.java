package com.tracetech.backend.repository;

import com.tracetech.backend.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Exact match by ingredient name (primary lookup)
    Optional<Inventory> findByIngredientName(String ingredientName);

    // Batch fetch for all ingredients in a given list
    // Used by StockCheckService to load all needed rows in one query
    @Query("SELECT i FROM Inventory i WHERE i.ingredientName IN :names")
    List<Inventory> findByIngredientNameIn(@Param("names") List<String> names);

    // Find all ingredients that are below their safety buffer threshold
    // Useful for a daily low-stock alert endpoint
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.currentQty < (i.currentQty * i.safetyBufferPct / 100)
        ORDER BY i.currentQty ASC
    """)
    List<Inventory> findLowStockItems();

    // Direct deduct stock — called after a day's production is confirmed
    @Modifying
    @Query("UPDATE Inventory i SET i.currentQty = i.currentQty - :used WHERE i.ingredientName = :name AND i.currentQty >= :used")
    int deductStock(@Param("name") String ingredientName, @Param("used") BigDecimal usedQty);

    // Direct restock — called when vendor logs a delivery
    @Modifying
    @Query("UPDATE Inventory i SET i.currentQty = i.currentQty + :added WHERE i.ingredientName = :name")
    int addStock(@Param("name") String ingredientName, @Param("added") BigDecimal addedQty);

    // Check if every ingredient from recipes has an inventory row
    @Query("SELECT i.ingredientName FROM Inventory i")
    List<String> findAllIngredientNames();
}
