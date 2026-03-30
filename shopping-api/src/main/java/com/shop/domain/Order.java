package com.shop.domain;

import com.shop.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long id;
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String currency;
    private String note;
    private String sqsMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // not persisted — populated by JOIN query when needed
    private List<OrderItem> items;
}
