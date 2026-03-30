package com.shop.service;

import com.shop.domain.Order;
import com.shop.dto.request.CreateOrderRequest;

import java.util.List;

public interface OrderService {

    /**
     * Places a flash-sale order:
     * 1. Acquire Redis distributed lock on inventory
     * 2. TCC tryLock inventory
     * 3. Persist order as PENDING
     * 4. Send to SQS for async confirmation
     * 5. Release lock
     *
     * Returns the PENDING order; caller should poll for final status.
     */
    Order createOrder(CreateOrderRequest request);

    Order getOrderById(Long id);

    List<Order> getOrdersByUserId(Long userId);
}
