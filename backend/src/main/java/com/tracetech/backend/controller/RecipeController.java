package com.tracetech.backend.controller;

import com.tracetech.backend.dto.IngredientRequirement;
import com.tracetech.backend.entity.Recipe;
import com.tracetech.backend.service.RecipeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    // GET /api/recipes/item/{id}
    // Returns all ingredient rows for one menu item
    @GetMapping("/item/{menuItemId}")
    public ResponseEntity<List<Recipe>> getRecipesForItem(@PathVariable Long menuItemId) {
        return ResponseEntity.ok(recipeService.getRecipesForItem(menuItemId));
    }

    // GET /api/recipes/item/{id}/requirements?predictedQty=137
    // Returns ingredient requirements for one item given a predicted quantity
    @GetMapping("/item/{menuItemId}/requirements")
    public ResponseEntity<List<IngredientRequirement>> getRequirements(
            @PathVariable Long menuItemId,
            @RequestParam int predictedQty) {
        return ResponseEntity.ok(recipeService.getRequirementsForItem(menuItemId, predictedQty));
    }

    // POST /api/recipes/daily-needs
    // Body: { "1": 90, "9": 137, "12": 60, ... }  (itemId → predictedQty)
    // Returns aggregated ingredient totals for the day
    @PostMapping("/daily-needs")
    public ResponseEntity<Map<String, IngredientRequirement>> getDailyNeeds(
            @RequestBody Map<Long, Integer> predictedQtyByItemId) {
        return ResponseEntity.ok(recipeService.calculateDailyIngredientNeeds(predictedQtyByItemId));
    }

    // GET /api/recipes/ingredients
    // Returns list of all distinct ingredient names (for inventory setup)
    @GetMapping("/ingredients")
    public ResponseEntity<List<String>> getAllIngredientNames() {
        return ResponseEntity.ok(recipeService.getAllIngredientNames());
    }

    // GET /api/recipes/validation/missing
    // Returns items that have no recipe rows yet — useful during setup
    @GetMapping("/validation/missing")
    public ResponseEntity<List<String>> getMissingRecipes() {
        return ResponseEntity.ok(recipeService.findItemsMissingRecipes());
    }

    // POST /api/recipes
    // Adds a single recipe row — used by admin/seed script
    @PostMapping
    public ResponseEntity<Recipe> addRecipe(@RequestBody AddRecipeRequest request) {
        Recipe saved = recipeService.addRecipe(
                request.menuItemId(),
                request.ingredientName(),
                request.qtyPerUnit(),
                request.unit()
        );
        return ResponseEntity.ok(saved);
    }

    // DELETE /api/recipes/{id}
    @DeleteMapping("/{recipeId}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable Long recipeId) {
        recipeService.deleteRecipe(recipeId);
        return ResponseEntity.noContent().build();
    }

    // Inner record for POST body
    record AddRecipeRequest(Long menuItemId, String ingredientName,
                            BigDecimal qtyPerUnit, String unit) {}
}
