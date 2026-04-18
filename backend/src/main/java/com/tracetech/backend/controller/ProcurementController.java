package com.tracetech.backend.controller;

import com.tracetech.backend.dto.IngredientRequirement;
import com.tracetech.backend.dto.StockCheckResult;
import com.tracetech.backend.entity.IngredientSupplier;
import com.tracetech.backend.service.ProcurementService;
import com.tracetech.backend.service.ProcurementService.ProcurementPlan;
import com.tracetech.backend.service.RecipeService;
import com.tracetech.backend.service.StockCheckService;
import com.tracetech.backend.service.StockCheckService.StockCheckSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/procurement")
public class ProcurementController {

    private final ProcurementService procurementService;
    private final RecipeService recipeService;
    private final StockCheckService stockCheckService;

    public ProcurementController(ProcurementService procurementService,
                                  RecipeService recipeService,
                                  StockCheckService stockCheckService) {
        this.procurementService = procurementService;
        this.recipeService      = recipeService;
        this.stockCheckService  = stockCheckService;
    }

    // POST /api/procurement/plan
    // Main endpoint — chains Steps 1+2+3 in one call.
    // Body: { "1": 90, "9": 137, ... }  (itemId → predictedQty)
    // Returns: full ProcurementPlan with order items, costs and urgency
    @PostMapping("/plan")
    public ResponseEntity<ProcurementPlan> getProcurementPlan(
            @RequestBody Map<Long, Integer> predictedQtyByItemId) {

        // Step 1: ingredient needs
        Map<String, IngredientRequirement> needs =
                recipeService.calculateDailyIngredientNeeds(predictedQtyByItemId);

        // Step 2: stock check
        StockCheckSummary stockSummary = stockCheckService.checkStock(needs);

        // Step 3: procurement plan (only for shortfalls + low stock)
        List<StockCheckResult> needToOrder = stockSummary.getShortfalls();
        needToOrder.addAll(stockSummary.getLow()); // also include LOW items
        ProcurementPlan plan = procurementService.buildProcurementPlan(needToOrder, needs);

        return ResponseEntity.ok(plan);
    }

    // GET /api/procurement/plan/today
    // Convenience — uses today's cached forecasts to build the plan.
    // Calls ForecastService internally to get today's predicted quantities.
    // Useful for the "Procurement Plan" page that auto-loads on open.
    @GetMapping("/plan/today")
    public ResponseEntity<ProcurementPlan> getTodayPlan(
            @RequestParam Map<Long, Integer> predictedQtyByItemId) {

        Map<String, IngredientRequirement> needs =
                recipeService.calculateDailyIngredientNeeds(predictedQtyByItemId);
        StockCheckSummary stock = stockCheckService.checkStock(needs);

        List<StockCheckResult> toOrder = stock.getShortfalls();
        toOrder.addAll(stock.getLow());

        return ResponseEntity.ok(procurementService.buildProcurementPlan(toOrder, needs));
    }

    // GET /api/procurement/suppliers
    // Returns all supplier rows (for admin management page)
    @GetMapping("/suppliers")
    public ResponseEntity<List<IngredientSupplier>> getAllSuppliers() {
        return ResponseEntity.ok(procurementService.getAllSuppliers());
    }

    // GET /api/procurement/suppliers/missing
    // Returns ingredients that have no supplier entry yet
    @GetMapping("/suppliers/missing")
    public ResponseEntity<List<String>> getMissingSuppliers() {
        return ResponseEntity.ok(procurementService.getMissingSuppliers());
    }

    // POST /api/procurement/suppliers
    // Add a new supplier entry for an ingredient
    // Body: { "ingredientName": "cooking oil", "costPerUnit": 120.00,
    //         "leadTimeDays": 1, "supplierName": "Chennai Wholesale" }
    @PostMapping("/suppliers")
    public ResponseEntity<IngredientSupplier> addSupplier(@RequestBody AddSupplierRequest req) {
        IngredientSupplier s = new IngredientSupplier(
                req.ingredientName(), req.costPerUnit(),
                req.leadTimeDays(), req.supplierName());
        if (req.minOrderQty() != null) s.setMinOrderQty(req.minOrderQty());
        return ResponseEntity.ok(procurementService.saveSupplier(s));
    }

    // PUT /api/procurement/suppliers/{ingredientName}
    // Update cost or lead time for an existing supplier
    @PutMapping("/suppliers/{ingredientName}")
    public ResponseEntity<IngredientSupplier> updateSupplier(
            @PathVariable String ingredientName,
            @RequestBody AddSupplierRequest req) {

        return procurementService.getSupplier(ingredientName)
                .map(existing -> {
                    existing.setCostPerUnit(req.costPerUnit());
                    existing.setLeadTimeDays(req.leadTimeDays());
                    if (req.supplierName() != null) existing.setSupplierName(req.supplierName());
                    if (req.minOrderQty()  != null) existing.setMinOrderQty(req.minOrderQty());
                    return ResponseEntity.ok(procurementService.saveSupplier(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    record AddSupplierRequest(String ingredientName, BigDecimal costPerUnit,
                               int leadTimeDays, String supplierName,
                               BigDecimal minOrderQty) {}
}
