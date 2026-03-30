package com.shop.service;

import com.shop.domain.Inventory;

public interface InventoryService {

    Inventory getByProductId(Long productId);

    Inventory initInventory(Long productId, int initialStock);

    Inventory updateStock(Long productId, int totalStock);

    Inventory replenish(Long productId, int additionalStock);
}
