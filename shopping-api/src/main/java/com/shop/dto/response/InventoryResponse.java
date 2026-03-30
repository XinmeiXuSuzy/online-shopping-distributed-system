package com.shop.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryResponse {

    private Long productId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockedQuantity;
    private LocalDateTime updatedAt;
}
