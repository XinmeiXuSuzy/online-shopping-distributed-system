package com.shop.exception;

public class LockAcquisitionException extends BusinessException {

    public LockAcquisitionException(String lockKey) {
        super(ErrorCode.LOCK_ACQUISITION_FAILED,
              "Failed to acquire distributed lock: " + lockKey);
    }
}
