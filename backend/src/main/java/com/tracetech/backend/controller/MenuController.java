package com.tracetech.backend.controller;

import com.tracetech.backend.dto.MenuItemResponse;
import com.tracetech.backend.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // GET /api/menu/items
    // Returns all 58 active menu items as flat list
    @GetMapping("/items")
    public ResponseEntity<List<MenuItemResponse>> getAllItems() {
        return ResponseEntity.ok(menuService.getAllItems());
    }

    // GET /api/menu/items/by-stall
    // Returns items grouped by stall — used by React data entry form
    @GetMapping("/items/by-stall")
    public ResponseEntity<Map<Integer, List<MenuItemResponse>>> getByStall() {
        return ResponseEntity.ok(menuService.getItemsByStall());
    }

    // GET /api/menu/items/by-cluster/{cluster}
    // Returns items in a specific cluster — used by ML service
    @GetMapping("/items/by-cluster/{cluster}")
    public ResponseEntity<List<MenuItemResponse>> getByCluster(
            @PathVariable String cluster) {
        return ResponseEntity.ok(menuService.getItemsByCluster(cluster));
    }
}
