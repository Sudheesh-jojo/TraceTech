package com.tracetech.backend.repository;

import com.tracetech.backend.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // All ingredients for a single menu item
    List<Recipe> findByMenuItemId(Long menuItemId);

    // All recipes for a list of item IDs (used for bulk daily calculation)
    @Query("SELECT r FROM Recipe r WHERE r.menuItem.id IN :itemIds")
    List<Recipe> findByMenuItemIdIn(@Param("itemIds") List<Long> itemIds);

    // Check if recipes exist for an item (validation helper)
    boolean existsByMenuItemId(Long menuItemId);

    // All distinct ingredient names across all recipes (for inventory seeding)
    @Query("SELECT DISTINCT r.ingredientName FROM Recipe r ORDER BY r.ingredientName")
    List<String> findAllDistinctIngredientNames();

    // All recipes grouped by ingredient (useful for aggregation queries)
    @Query("SELECT r FROM Recipe r WHERE r.ingredientName = :ingredientName")
    List<Recipe> findByIngredientName(@Param("ingredientName") String ingredientName);
}
