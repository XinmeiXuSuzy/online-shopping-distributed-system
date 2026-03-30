package com.shop.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 4xx client errors
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    VALIDATION_ERROR(400, "Validation failed"),
    INSUFFICIENT_INVENTORY(409, "Insufficient inventory"),
    LOCK_ACQUISITION_FAILED(429, "System busy, please retry"),
    ORDER_ALREADY_PROCESSED(409, "Order already processed"),

    // 5xx server errors
    INTERNAL_ERROR(500, "Internal server error"),
    SQS_SEND_FAILED(500, "Failed to queue order for processing");

    private final int httpStatus;
    private final String defaultMessage;
}
