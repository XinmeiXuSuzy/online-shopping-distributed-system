package com.shop.lock;

/**
 * Centralizes Redis lock key naming to prevent collisions.
 */
public final class LockKeyBuilder {

    private static final String PREFIX = "lock:";

    private LockKeyBuilder() {}

    public static String inventoryKey(Long productId) {
        return PREFIX + "inventory:" + productId;
    }

    public static String orderKey(Long orderId) {
        return PREFIX + "order:" + orderId;
    }

    public static String userKey(Long userId) {
        return PREFIX + "user:" + userId;
    }
}
