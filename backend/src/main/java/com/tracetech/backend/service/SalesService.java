package com.tracetech.backend.service;

import com.tracetech.backend.dto.SalesSubmitRequest;
import com.tracetech.backend.dto.SalesSubmitResponse;
import com.tracetech.backend.entity.MenuItem;
import com.tracetech.backend.entity.SalesActual;
import com.tracetech.backend.repository.MenuItemRepository;
import com.tracetech.backend.repository.SalesActualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesService {

    private final SalesActualRepository salesActualRepository;
    private final MenuItemRepository menuItemRepository;

    // ── Submit actual sales for one item ───────────────────────
    public SalesSubmitResponse submitSales(SalesSubmitRequest request) {

        MenuItem item = menuItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new RuntimeException("Item not found: " + request.getItemId()));

        // Check if already submitted for this date
        if (salesActualRepository.existsByMenuItem_IdAndSaleDate(
                request.getItemId(), request.getSaleDate())) {
            throw new RuntimeException("Sales already submitted for "
                    + item.getName() + " on " + request.getSaleDate());
        }

        int qtySold     = request.getQtySold();
        int qtyPrepared = request.getQtyPrepared();
        int qtyWasted   = Math.max(0, qtyPrepared - qtySold);

        BigDecimal costPerUnit = item.getIngredientCostPerUnit();
        BigDecimal sellingPrice = item.getSellingPrice();

        BigDecimal wasteCost = costPerUnit
                .multiply(BigDecimal.valueOf(qtyWasted))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal revenue = sellingPrice
                .multiply(BigDecimal.valueOf(qtySold))
                .setScale(2, RoundingMode.HALF_UP);

        SalesActual sale = SalesActual.builder()
                .menuItem(item)
                .saleDate(request.getSaleDate())
                .qtySold(qtySold)
                .qtyPrepared(qtyPrepared)
                .qtyWasted(qtyWasted)
                .revenue(revenue)
                .wasteCost(wasteCost)
                .build();

        SalesActual saved = salesActualRepository.save(sale);

        log.info("Sales submitted — {} | sold: {} | wasted: {} | waste cost: ₹{}",
                item.getName(), qtySold, qtyWasted, wasteCost);

        return SalesSubmitResponse.builder()
                .salesId(saved.getId())
                .itemName(item.getName())
                .qtySold(qtySold)
                .qtyPrepared(qtyPrepared)
                .qtyWasted(qtyWasted)
                .wasteCost(wasteCost.doubleValue())
                .revenue(revenue.doubleValue())
                .message(qtyWasted > 0
                        ? qtyWasted + " units wasted — ₹" + wasteCost + " lost"
                        : "No waste today!")
                .build();
    }

    // ── Submit sales for multiple items in one call ─────────────
    @Transactional
    public List<SalesSubmitResponse> submitBulkSales(List<SalesSubmitRequest> requests) {
        return requests.stream()
                .map(this::submitSales)
                .toList();
    }
}
