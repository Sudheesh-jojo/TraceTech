package com.tracetech.backend.service;

import com.tracetech.backend.entity.Inventory;
import com.tracetech.backend.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * InventoryService — thin CRUD wrapper over InventoryRepository.
 * Keeps controller lean. Heavy stock-check logic stays in StockCheckService.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public List<Inventory> getAll() {
        return inventoryRepository.findAll();
    }

    public Optional<Inventory> findByName(String ingredientName) {
        return inventoryRepository.findByIngredientName(ingredientName);
    }

    @Transactional
    public Inventory add(String ingredientName, BigDecimal currentQty, String unit) {
        Inventory inv = new Inventory(ingredientName, currentQty, unit);
        return inventoryRepository.save(inv);
    }

    @Transactional
    public boolean updateSafetyBuffer(String ingredientName, BigDecimal bufferPct) {
        return inventoryRepository.findByIngredientName(ingredientName)
                .map(inv -> {
                    inv.setSafetyBufferPct(bufferPct);
                    inventoryRepository.save(inv);
                    return true;
                })
                .orElse(false);
    }
}
