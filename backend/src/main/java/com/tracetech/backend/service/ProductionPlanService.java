package com.tracetech.backend.service;

import com.tracetech.backend.dto.ProductionPlanItem;
import com.tracetech.backend.entity.Inventory;
import com.tracetech.backend.entity.MenuItem;
import com.tracetech.backend.entity.Recipe;
import com.tracetech.backend.entity.StallCapacity;
import com.tracetech.backend.repository.InventoryRepository;
import com.tracetech.backend.repository.MenuItemRepository;
import com.tracetech.backend.repository.RecipeRepository;
import com.tracetech.backend.repository.StallCapacityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProductionPlanService — Step 4 of TraceTech supply chain.
 *
 * INTEGRATION CONTRACT:
 *
 *   Input:  Map<Long, Integer>  predictedQtyByItemId
 *           — same map used throughout Steps 1-3 (itemId → predictedQty)
 *           — comes from ForecastService or directly from controller
 *
 *   Output: ProductionPlan
 *           — List<ProductionPlanItem> one per menu item
 *           — grouped by stall for easy frontend rendering
 *           — summary: totalActualUnits, totalConstrainedItems, totalWasteRisk
 *
 * THREE CONSTRAINTS APPLIED PER ITEM (in this order):
 *   1. ingredientMaxUnits — how many units stock allows (from Inventory + Recipe)
 *   2. perItemCap         — stall's max units for any single item (from StallCapacity)
 *   3. stallTotalCap      — stall's overall daily total (shared across all stall items)
 *
 * DOES NOT MODIFY: ForecastService, RecipeService, StockCheckService,
 *                  ProcurementService, or any ML code.
 */
@Service
@Transactional(readOnly = true)
public class ProductionPlanService {

    private final MenuItemRepository      menuItemRepository;
    private final RecipeRepository        recipeRepository;
    private final InventoryRepository     inventoryRepository;
    private final StallCapacityRepository stallCapacityRepository;

    @Value("${tracetech.production.unconstrained-max}")
    private int unconstrainedMax;

    public ProductionPlanService(MenuItemRepository menuItemRepository,
                                  RecipeRepository recipeRepository,
                                  InventoryRepository inventoryRepository,
                                  StallCapacityRepository stallCapacityRepository) {
        this.menuItemRepository      = menuItemRepository;
        this.recipeRepository        = recipeRepository;
        this.inventoryRepository     = inventoryRepository;
        this.stallCapacityRepository = stallCapacityRepository;
    }

    // -------------------------------------------------------------------------
    // CORE: Build full production plan for all items
    // -------------------------------------------------------------------------
    public ProductionPlan buildProductionPlan(Map<Long, Integer> predictedQtyByItemId) {

        // --- Load all required data in bulk (minimise DB round trips) ---

        List<Long> itemIds = new ArrayList<>(predictedQtyByItemId.keySet());

        // All menu items
        Map<Long, MenuItem> itemMap = menuItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, i -> i));

        // All recipes for all items (single query)
        List<Recipe> allRecipes = recipeRepository.findByMenuItemIdIn(itemIds);
        Map<Long, List<Recipe>> recipesByItem = allRecipes.stream()
                .collect(Collectors.groupingBy(r -> r.getMenuItem().getId()));

        // All unique ingredient names needed
        List<String> ingredientNames = allRecipes.stream()
                .map(Recipe::getIngredientName)
                .distinct()
                .collect(Collectors.toList());

        // All relevant inventory rows (single query)
        Map<String, Inventory> inventoryMap = inventoryRepository
                .findByIngredientNameIn(ingredientNames).stream()
                .collect(Collectors.toMap(Inventory::getIngredientName, i -> i));

        // All stall capacities (only 4 rows — tiny query)
        Map<Integer, StallCapacity> capacityMap = stallCapacityRepository
                .findAllByOrderByStallIdAsc().stream()
                .collect(Collectors.toMap(StallCapacity::getStallId, c -> c));

        // Track stall usage as items are planned (capacity is shared across items in same stall)
        Map<Integer, Integer> stallUsedUnits = new HashMap<>();
        for (Integer stallId : capacityMap.keySet()) {
            stallUsedUnits.put(stallId, 0);
        }

        // --- Build plan item by item ---
        List<ProductionPlanItem> planItems = new ArrayList<>();

        for (Long itemId : itemIds) {
            Integer predicted = predictedQtyByItemId.get(itemId);
            if (predicted == null || predicted <= 0) continue;

            MenuItem item = itemMap.get(itemId);
            if (item == null) continue;

            int stallId = item.getStallId();
            StallCapacity cap = capacityMap.get(stallId);

            // --- Constraint 1: Ingredient-based maximum ---
            List<Recipe> recipes = recipesByItem.getOrDefault(itemId, Collections.emptyList());
            int ingredientMax = calculateIngredientMaxUnits(recipes, inventoryMap);
            String bottleneck = findBottleneckIngredient(recipes, inventoryMap, ingredientMax);

            // --- Constraint 2: Per-item cap within stall ---
            int perItemCap = (cap != null && cap.getMaxUnitsPerItem() > 0)
                    ? cap.getMaxUnitsPerItem()
                    : Integer.MAX_VALUE;

            // --- Constraint 3: Remaining stall daily capacity ---
            int stallRemaining = Integer.MAX_VALUE;
            if (cap != null) {
                int used = stallUsedUnits.getOrDefault(stallId, 0);
                stallRemaining = Math.max(0, cap.getMaxTotalUnitsPerDay() - used);
            }

            // Effective stall cap = min(perItemCap, stallRemaining)
            int effectiveStallCap = Math.min(perItemCap, stallRemaining);

            // Build the plan item (constructor applies the min() formula internally)
            ProductionPlanItem planItem = new ProductionPlanItem(
                    itemId,
                    item.getName(),
                    stallId,
                    cap != null ? cap.getStallName() : "Unknown stall",
                    predicted,
                    ingredientMax,
                    effectiveStallCap,
                    bottleneck,
                    item.getIngredientCostPerUnit()
            );

            // Deduct from stall used units
            stallUsedUnits.merge(stallId, planItem.getActualProduce(), Integer::sum);

            planItems.add(planItem);
        }

        return new ProductionPlan(planItems);
    }

    // -------------------------------------------------------------------------
    // HELPER: Given current inventory, how many units of this item can be made?
    // Returns the minimum across all ingredients (the bottleneck ingredient wins)
    // -------------------------------------------------------------------------
    private int calculateIngredientMaxUnits(List<Recipe> recipes,
                                             Map<String, Inventory> inventoryMap) {
        if (recipes.isEmpty()) return Integer.MAX_VALUE; // no recipe = no constraint

        int maxUnits = Integer.MAX_VALUE;

        for (Recipe recipe : recipes) {
            Inventory inv = inventoryMap.get(recipe.getIngredientName());
            if (inv == null) continue; // missing inventory row = skip constraint

            BigDecimal qtyPerUnit = recipe.getQtyPerUnit();
            if (qtyPerUnit.compareTo(BigDecimal.ZERO) <= 0) continue; // in-house items

            // How many units can this ingredient support?
            int possible = inv.getCurrentQty()
                    .divide(qtyPerUnit, 0, RoundingMode.FLOOR)
                    .intValue();

            maxUnits = Math.min(maxUnits, possible);
        }

        return maxUnits == Integer.MAX_VALUE ? unconstrainedMax : maxUnits;
    }

    // -------------------------------------------------------------------------
    // HELPER: Name the specific ingredient that caused the constraint
    // -------------------------------------------------------------------------
    private String findBottleneckIngredient(List<Recipe> recipes,
                                             Map<String, Inventory> inventoryMap,
                                             int maxUnits) {
        for (Recipe recipe : recipes) {
            Inventory inv = inventoryMap.get(recipe.getIngredientName());
            if (inv == null) continue;

            BigDecimal qtyPerUnit = recipe.getQtyPerUnit();
            if (qtyPerUnit.compareTo(BigDecimal.ZERO) <= 0) continue;

            int possible = inv.getCurrentQty()
                    .divide(qtyPerUnit, 0, RoundingMode.FLOOR)
                    .intValue();

            if (possible == maxUnits) return recipe.getIngredientName();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // READ: Get stall capacities (for admin page)
    // -------------------------------------------------------------------------
    public List<StallCapacity> getAllCapacities() {
        return stallCapacityRepository.findAllByOrderByStallIdAsc();
    }

    // -------------------------------------------------------------------------
    // WRITE: Update a stall's capacity (vendor adjusts for staff changes etc.)
    // -------------------------------------------------------------------------
    @Transactional
    public StallCapacity updateCapacity(Integer stallId, int maxTotal, int maxPerItem) {
        return stallCapacityRepository.findByStallId(stallId)
                .map(cap -> {
                    cap.setMaxTotalUnitsPerDay(maxTotal);
                    cap.setMaxUnitsPerItem(maxPerItem);
                    return stallCapacityRepository.save(cap);
                })
                .orElseThrow(() -> new RuntimeException("Stall not found: " + stallId));
    }

    // =========================================================================
    // INNER CLASS: Full production plan
    // =========================================================================
    public static class ProductionPlan {

        private final List<ProductionPlanItem> items;
        private final Map<Integer, List<ProductionPlanItem>> byStall;

        private final int totalPredicted;
        private final int totalActualProduce;
        private final int totalConstrainedItems;  // items where actualProduce < predicted
        private final int ingredientConstrainedCount;
        private final int capacityConstrainedCount;
        private final BigDecimal totalIngredientCost;

        public ProductionPlan(List<ProductionPlanItem> items) {
            this.items = items;
            this.byStall = items.stream()
                    .collect(Collectors.groupingBy(ProductionPlanItem::getStallId));

            this.totalPredicted = items.stream()
                    .mapToInt(ProductionPlanItem::getPredictedQty).sum();
            this.totalActualProduce = items.stream()
                    .mapToInt(ProductionPlanItem::getActualProduce).sum();
            this.totalConstrainedItems = (int) items.stream()
                    .filter(ProductionPlanItem::isFullyConstrained).count();
            this.ingredientConstrainedCount = (int) items.stream()
                    .filter(ProductionPlanItem::isIngredientLimited).count();
            this.capacityConstrainedCount = (int) items.stream()
                    .filter(ProductionPlanItem::isCapacityLimited).count();
            this.totalIngredientCost = items.stream()
                    .map(ProductionPlanItem::getTotalIngredientCost)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public List<ProductionPlanItem> getItems()                       { return items; }
        public Map<Integer, List<ProductionPlanItem>> getByStall()       { return byStall; }
        public int getTotalPredicted()                                    { return totalPredicted; }
        public int getTotalActualProduce()                                { return totalActualProduce; }
        public int getTotalConstrainedItems()                             { return totalConstrainedItems; }
        public int getIngredientConstrainedCount()                        { return ingredientConstrainedCount; }
        public int getCapacityConstrainedCount()                          { return capacityConstrainedCount; }
        public BigDecimal getTotalIngredientCost()                        { return totalIngredientCost; }
        public boolean hasConstraints()                                   { return totalConstrainedItems > 0; }
    }
}
