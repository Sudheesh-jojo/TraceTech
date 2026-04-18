package com.tracetech.backend.controller;

import com.tracetech.backend.dto.IngredientRequirement;
import com.tracetech.backend.dto.StockCheckResult;
import com.tracetech.backend.entity.Inventory;
import com.tracetech.backend.service.InventoryService;
import com.tracetech.backend.service.RecipeService;
import com.tracetech.backend.service.StockCheckService;
import com.tracetech.backend.service.StockCheckService.StockCheckSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockCheckService stockCheckService;
    private final RecipeService recipeService;

    public InventoryController(InventoryService inventoryService,
                                StockCheckService stockCheckService,
                                RecipeService recipeService) {
        this.inventoryService = inventoryService;
        this.stockCheckService = stockCheckService;
        this.recipeService = recipeService;
    }

    // GET /api/inventory or /api/inventory/
    // Returns all inventory rows — for the vendor's stock management page
    @GetMapping({"", "/"})
    public ResponseEntity<List<Inventory>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAll());
    }

    // GET /api/inventory/{ingredientName}
    // Returns stock for a single ingredient
    @GetMapping("/{ingredientName}")
    public ResponseEntity<Inventory> getOne(@PathVariable String ingredientName) {
        return inventoryService.findByName(ingredientName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/inventory
    // Add a new inventory row (used during initial setup)
    // Body: { "ingredientName": "cooking oil", "currentQty": 5.0, "unit": "litres" }
    @PostMapping
    public ResponseEntity<Inventory> addInventory(@RequestBody AddInventoryRequest req) {
        Inventory saved = inventoryService.add(req.ingredientName(), req.currentQty(), req.unit());
        return ResponseEntity.ok(saved);
    }

    // PUT /api/inventory/{ingredientName}/restock
    // Vendor logs a delivery — adds qty to current stock
    // Body: { "qty": 10.0 }
    @PutMapping("/{ingredientName}/restock")
    public ResponseEntity<String> restock(@PathVariable String ingredientName,
                                           @RequestBody QtyRequest req) {
        boolean ok = stockCheckService.restock(ingredientName, req.qty());
        return ok ? ResponseEntity.ok("Restocked " + ingredientName + " by " + req.qty())
                  : ResponseEntity.notFound().build();
    }

    // PUT /api/inventory/{ingredientName}/deduct
    // Deduct stock after confirmed production
    // Body: { "qty": 3.5 }
    @PutMapping("/{ingredientName}/deduct")
    public ResponseEntity<String> deduct(@PathVariable String ingredientName,
                                          @RequestBody QtyRequest req) {
        boolean ok = stockCheckService.deductStock(ingredientName, req.qty());
        return ok ? ResponseEntity.ok("Deducted " + req.qty() + " from " + ingredientName)
                  : ResponseEntity.badRequest().body("Insufficient stock or ingredient not found");
    }

    // PUT /api/inventory/{ingredientName}/buffer
    // Update safety buffer percentage for an ingredient
    // Body: { "qty": 15.0 }  (represents percent e.g. 15 = 15%)
    @PutMapping("/{ingredientName}/buffer")
    public ResponseEntity<String> updateBuffer(@PathVariable String ingredientName,
                                                @RequestBody QtyRequest req) {
        boolean ok = inventoryService.updateSafetyBuffer(ingredientName, req.qty());
        return ok ? ResponseEntity.ok("Buffer updated")
                  : ResponseEntity.notFound().build();
    }

    // POST /api/inventory/stock-check
    // Run a full stock check against a given prediction map
    // Body: { "1": 90, "9": 137, ... }  (itemId → predictedQty)
    // Returns full StockCheckSummary with ok/low/shortfall breakdown
    @PostMapping("/stock-check")
    public ResponseEntity<StockCheckSummary> runStockCheck(
            @RequestBody Map<Long, Integer> predictedQtyByItemId) {
        Map<String, IngredientRequirement> needs =
                recipeService.calculateDailyIngredientNeeds(predictedQtyByItemId);
        StockCheckSummary summary = stockCheckService.checkStock(needs);
        return ResponseEntity.ok(summary);
    }

    // GET /api/inventory/low-stock
    // Returns ingredients below safety buffer — for dashboard alert
    @GetMapping("/low-stock")
    public ResponseEntity<List<Inventory>> getLowStock() {
        return ResponseEntity.ok(stockCheckService.getLowStockItems());
    }

    // GET /api/inventory/missing
    // Returns ingredient names that exist in recipes but have no inventory row
    @GetMapping("/missing")
    public ResponseEntity<List<String>> getMissingInventory() {
        List<String> allIngredients = recipeService.getAllIngredientNames();
        return ResponseEntity.ok(stockCheckService.findIngredientsMissingFromInventory(allIngredients));
    }

    // Records for request bodies
    record AddInventoryRequest(String ingredientName, BigDecimal currentQty, String unit) {}
    record QtyRequest(BigDecimal qty) {}
}
