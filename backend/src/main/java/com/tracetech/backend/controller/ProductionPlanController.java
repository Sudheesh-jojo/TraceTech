package com.tracetech.backend.controller;

import com.tracetech.backend.entity.StallCapacity;
import com.tracetech.backend.service.ProductionPlanService;
import com.tracetech.backend.service.ProductionPlanService.ProductionPlan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production")
public class ProductionPlanController {

    private final ProductionPlanService productionPlanService;

    public ProductionPlanController(ProductionPlanService productionPlanService) {
        this.productionPlanService = productionPlanService;
    }

    // POST /api/production/plan
    // Main endpoint — build production plan from prediction map.
    // Body: { "1": 90, "9": 137, ... }  (itemId → predictedQty)
    //
    // Returns ProductionPlan with:
    //   items[]            — per-item actual_produce, limiting_factor, waste_risk
    //   byStall{}          — same items grouped by stall_id
    //   totalPredicted     — sum of all ML predictions
    //   totalActualProduce — sum after constraints applied
    //   totalConstrainedItems  — how many items were capped
    //   totalIngredientCost    — estimated raw material cost for today's run
    @PostMapping("/plan")
    public ResponseEntity<ProductionPlan> getProductionPlan(
            @RequestBody Map<Long, Integer> predictedQtyByItemId) {
        return ResponseEntity.ok(
                productionPlanService.buildProductionPlan(predictedQtyByItemId)
        );
    }

    // GET /api/production/capacity
    // Returns all 4 stall capacity rows — for admin/settings page
    @GetMapping("/capacity")
    public ResponseEntity<List<StallCapacity>> getAllCapacities() {
        return ResponseEntity.ok(productionPlanService.getAllCapacities());
    }

    // PUT /api/production/capacity/{stallId}
    // Update stall capacity — vendor adjusts when staffing changes
    // Body: { "maxTotal": 700, "maxPerItem": 200 }
    @PutMapping("/capacity/{stallId}")
    public ResponseEntity<StallCapacity> updateCapacity(
            @PathVariable Integer stallId,
            @RequestBody CapacityUpdateRequest req) {
        return ResponseEntity.ok(
                productionPlanService.updateCapacity(stallId, req.maxTotal(), req.maxPerItem())
        );
    }

    record CapacityUpdateRequest(int maxTotal, int maxPerItem) {}
}
