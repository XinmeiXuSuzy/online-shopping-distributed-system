package com.shop.domain;

import com.shop.domain.enums.TccStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TccTransaction {

    private Long id;
    private Long orderId;
    private Long productId;
    private Integer quantity;
    private TccStatus status;
    private LocalDateTime tryExpireAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
