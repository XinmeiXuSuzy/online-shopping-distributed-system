package com.shop.service.tcc;

/**
 * TCC (Try-Confirm-Cancel) inventory coordinator.
 *
 * Try:     Reserve inventory atomically; creates a TCC journal entry.
 * Confirm: Commit the reservation — deducts from actual stock.
 * Cancel:  Roll back the reservation — returns units to available stock.
 *
 * Idempotency: all three phases are safe to call multiple times for the same orderId.
 */
public interface TccInventoryService {

    /**
     * Try phase: acquires a reservation on #{quantity} units of #{productId} for #{orderId}.
     *
     * @throws com.shop.exception.InsufficientInventoryException if available_stock < quantity
     */
    void tryLock(Long orderId, Long productId, int quantity);

    /**
     * Confirm phase: converts the reservation into a committed deduction.
     */
    void confirm(Long orderId);

    /**
     * Cancel phase: releases the reservation and returns units to available stock.
     *
     * @param reason human-readable explanation stored in tcc_transactions.cancel_reason
     */
    void cancel(Long orderId, String reason);
}
