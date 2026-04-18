package com.tracetech.backend.service;

import com.tracetech.backend.dto.IngredientRequirement;
import com.tracetech.backend.entity.MenuItem;
import com.tracetech.backend.entity.Recipe;
import com.tracetech.backend.repository.RecipeRepository;
import com.tracetech.backend.repository.MenuItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final MenuItemRepository menuItemRepository;

    public RecipeService(RecipeRepository recipeRepository, MenuItemRepository menuItemRepository) {
        this.recipeRepository = recipeRepository;
        this.menuItemRepository = menuItemRepository;
    }

    // -------------------------------------------------------------------------
    // CORE: Calculate ingredient requirements for a single item
    // Called per-item during forecast enrichment
    // -------------------------------------------------------------------------
    public List<IngredientRequirement> getRequirementsForItem(Long menuItemId, int predictedQty) {
        List<Recipe> recipes = recipeRepository.findByMenuItemId(menuItemId);
        MenuItem item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new RuntimeException("MenuItem not found: " + menuItemId));

        return recipes.stream()
                .map(recipe -> new IngredientRequirement(
                        item.getId(),
                        item.getName(),
                        predictedQty,
                        recipe.getIngredientName(),
                        recipe.getQtyPerUnit(),
                        recipe.getUnit()
                ))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // CORE: Calculate aggregated ingredient totals across ALL 58 items
    // Input:  Map<menuItemId, predictedQty>  — comes straight from ForecastService
    // Output: Map<ingredientName, IngredientRequirement> — one entry per ingredient,
    //         totalQtyNeeded already summed across all items that use it
    //
    // Example: cooking oil appears in Samosa, Dosa, Fries → totals are summed here
    // -------------------------------------------------------------------------
    public Map<String, IngredientRequirement> calculateDailyIngredientNeeds(
            Map<Long, Integer> predictedQtyByItemId) {

        List<Long> itemIds = new ArrayList<>(predictedQtyByItemId.keySet());
        List<Recipe> allRecipes = recipeRepository.findByMenuItemIdIn(itemIds);

        // Accumulate totals: ingredientName → running total
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();

        for (Recipe recipe : allRecipes) {
            Long itemId = recipe.getMenuItem().getId();
            int qty = predictedQtyByItemId.getOrDefault(itemId, 0);

            BigDecimal lineTotal = recipe.getQtyPerUnit()
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(4, RoundingMode.HALF_UP);

            totals.merge(recipe.getIngredientName(), lineTotal, BigDecimal::add);
            units.putIfAbsent(recipe.getIngredientName(), recipe.getUnit());
        }

        // Build result map
        Map<String, IngredientRequirement> result = new LinkedHashMap<>();
        totals.forEach((ingredientName, total) ->
                result.put(ingredientName, new IngredientRequirement(
                        ingredientName,
                        total.setScale(3, RoundingMode.HALF_UP),
                        units.get(ingredientName)
                ))
        );

        return result;
    }

    // -------------------------------------------------------------------------
    // READ: Get all recipes for one item (for display / admin)
    // -------------------------------------------------------------------------
    public List<Recipe> getRecipesForItem(Long menuItemId) {
        return recipeRepository.findByMenuItemId(menuItemId);
    }

    // -------------------------------------------------------------------------
    // READ: Get all distinct ingredient names (for inventory table seeding)
    // -------------------------------------------------------------------------
    public List<String> getAllIngredientNames() {
        return recipeRepository.findAllDistinctIngredientNames();
    }

    // -------------------------------------------------------------------------
    // WRITE: Save a new recipe row (admin / seed use)
    // -------------------------------------------------------------------------
    @Transactional
    public Recipe addRecipe(Long menuItemId, String ingredientName,
                             BigDecimal qtyPerUnit, String unit) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new RuntimeException("MenuItem not found: " + menuItemId));

        Recipe recipe = new Recipe(item, ingredientName, qtyPerUnit, unit);
        return recipeRepository.save(recipe);
    }

    // -------------------------------------------------------------------------
    // WRITE: Delete a recipe row
    // -------------------------------------------------------------------------
    @Transactional
    public void deleteRecipe(Long recipeId) {
        recipeRepository.deleteById(recipeId);
    }

    // -------------------------------------------------------------------------
    // VALIDATION: Check if all 58 active items have at least one recipe row
    // Useful at startup or in a health check endpoint
    // -------------------------------------------------------------------------
    public List<String> findItemsMissingRecipes() {
        List<MenuItem> activeItems = menuItemRepository.findByIsActiveTrue();
        List<String> missing = new ArrayList<>();

        for (MenuItem item : activeItems) {
            if (!recipeRepository.existsByMenuItemId(item.getId())) {
                missing.add(item.getName() + " (id=" + item.getId() + ")");
            }
        }
        return missing;
    }
}
