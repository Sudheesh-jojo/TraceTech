package com.tracetech.backend.service;

import com.tracetech.backend.dto.MenuItemResponse;
import com.tracetech.backend.entity.MenuItem;
import com.tracetech.backend.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository menuItemRepository;

    // ── All active items as flat list ───────────────────────
    public List<MenuItemResponse> getAllItems() {
        return menuItemRepository.findByIsActiveTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Items grouped by stall ──────────────────────────────
    public Map<Integer, List<MenuItemResponse>> getItemsByStall() {
        return menuItemRepository.findByIsActiveTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.groupingBy(MenuItemResponse::getStallId));
    }

    // ── Items by cluster ────────────────────────────────────
    public List<MenuItemResponse> getItemsByCluster(String cluster) {
        return menuItemRepository.findByCluster(cluster)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private MenuItemResponse toResponse(MenuItem item) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .category(item.getCategory())
                .stallId(item.getStallId())
                .cluster(item.getCluster())
                .mealPeriod(item.getMealPeriod())
                .sellingPrice(item.getSellingPrice().doubleValue())
                .ingredientCostPerUnit(item.getIngredientCostPerUnit().doubleValue())
                .baseDailyQty(item.getBaseDailyQty())
                .isActive(item.getIsActive())
                .build();
    }
}
