package com.tracetech.backend.repository;

import com.tracetech.backend.entity.IngredientSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientSupplierRepository extends JpaRepository<IngredientSupplier, Long> {

    Optional<IngredientSupplier> findByIngredientName(String ingredientName);

    // Batch fetch for all shortfall ingredients — one query for entire order
    @Query("SELECT s FROM IngredientSupplier s WHERE s.ingredientName IN :names")
    List<IngredientSupplier> findByIngredientNameIn(@Param("names") List<String> names);

    // Find ingredients that have no supplier row yet
    @Query("""
        SELECT i.ingredientName FROM Inventory i
        WHERE i.ingredientName NOT IN (
            SELECT s.ingredientName FROM IngredientSupplier s
        )
        ORDER BY i.ingredientName
    """)
    List<String> findInventoryIngredientsMissingSupplier();

    // All suppliers ordered by lead time — useful for urgency prioritisation
    List<IngredientSupplier> findAllByOrderByLeadTimeDaysDesc();
}
