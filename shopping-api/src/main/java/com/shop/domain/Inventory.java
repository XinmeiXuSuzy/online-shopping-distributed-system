package com.shop.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    private Long id;
    private Long productId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockedQuantity;
    private Integer version;
    private LocalDateTime updatedAt;
}
