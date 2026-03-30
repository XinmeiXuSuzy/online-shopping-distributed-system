package com.shop.service.sqs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message payload placed on the SQS order processing queue.
 * Kept small — only IDs; the consumer looks up full data from the DB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {

    private Long orderId;
    private Long productId;
    private int  quantity;
    private Long userId;
}
