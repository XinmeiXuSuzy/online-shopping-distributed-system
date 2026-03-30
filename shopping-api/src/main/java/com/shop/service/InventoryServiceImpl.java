package com.shop.service;

import com.shop.domain.Inventory;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.InventoryMapper;
import com.shop.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryMapper inventoryMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    @Transactional(readOnly = true)
    public Inventory getByProductId(Long productId) {
        return inventoryMapper.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", productId));
    }

    @Override
    @Transactional
    public Inventory initInventory(Long productId, int initialStock) {
        Inventory inventory = Inventory.builder()
                .id(idGenerator.nextId())
                .productId(productId)
                .totalStock(initialStock)
                .availableStock(initialStock)
                .lockedQuantity(0)
                .version(0)
                .build();
        inventoryMapper.insert(inventory);
        log.info("Initialized inventory for productId={}, stock={}", productId, initialStock);
        return inventory;
    }

    @Override
    @Transactional
    public Inventory updateStock(Long productId, int totalStock) {
        inventoryMapper.updateTotalStock(productId, totalStock);
        return getByProductId(productId);
    }

    @Override
    @Transactional
    public Inventory replenish(Long productId, int additionalStock) {
        inventoryMapper.replenish(productId, additionalStock);
        log.info("Replenished productId={} by {}", productId, additionalStock);
        return getByProductId(productId);
    }
}
