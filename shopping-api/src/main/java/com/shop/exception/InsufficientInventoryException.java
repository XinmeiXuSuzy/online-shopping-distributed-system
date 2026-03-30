package com.shop.exception;

public class InsufficientInventoryException extends BusinessException {

    public InsufficientInventoryException(Long productId, int requested) {
        super(ErrorCode.INSUFFICIENT_INVENTORY,
              "Insufficient inventory for product " + productId + ", requested: " + requested);
    }
}
