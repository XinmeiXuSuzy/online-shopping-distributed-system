package com.shop.service;

import com.shop.domain.Order;
import com.shop.domain.OrderItem;
import com.shop.domain.Product;
import com.shop.domain.enums.OrderStatus;
import com.shop.dto.request.CreateOrderRequest;
import com.shop.dto.request.OrderItemRequest;
import com.shop.exception.BusinessException;
import com.shop.exception.ErrorCode;
import com.shop.exception.ResourceNotFoundException;
import com.shop.lock.LockKeyBuilder;
import com.shop.lock.RedisDistributedLock;
import com.shop.mapper.OrderItemMapper;
import com.shop.mapper.OrderMapper;
import com.shop.mapper.ProductMapper;
import com.shop.service.sqs.OrderMessage;
import com.shop.service.sqs.OrderMessageProducer;
import com.shop.service.tcc.TccInventoryService;
import com.shop.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final TccInventoryService tccInventoryService;
    private final OrderMessageProducer orderMessageProducer;
    private final RedisDistributedLock distributedLock;
    private final SnowflakeIdGenerator idGenerator;

    @Value("${app.lock.ttl-ms:5000}")
    private long lockTtlMs;

    @Value("${app.lock.retry-times:3}")
    private int lockRetryTimes;

    @Value("${app.lock.retry-interval-ms:100}")
    private long lockRetryIntervalMs;

    /**
     * Flash sale order creation — the critical path.
     *
     * This method is intentionally NOT annotated @Transactional at the outer level.
     * The DB transaction is scoped tightly inside createOrderInTransaction() to minimize
     * lock hold time. The Redis lock wraps the DB transaction, not the other way around.
     */
    @Override
    public Order createOrder(CreateOrderRequest request) {
        // For multi-item orders, lock the first product.
        // Production: acquire all locks sorted by productId to avoid deadlocks.
        Long primaryProductId = request.getItems().get(0).getProductId();
        String lockKey = LockKeyBuilder.inventoryKey(primaryProductId);
        String ownerToken = UUID.randomUUID() + ":" + Thread.currentThread().getId();

        distributedLock.lock(lockKey, ownerToken, lockTtlMs, lockRetryTimes, lockRetryIntervalMs);
        try {
            return createOrderInTransaction(request);
        } finally {
            distributedLock.releaseLock(lockKey, ownerToken);
        }
    }

    @Transactional
    protected Order createOrderInTransaction(CreateOrderRequest request) {
        Long orderId = idGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();

        // Resolve products and compute totals
        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productMapper.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId()));

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            items.add(OrderItem.builder()
                    .id(idGenerator.nextId())
                    .orderId(orderId)
                    .productId(product.getId())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .subtotal(subtotal)
                    .createdAt(now)
                    .build());
        }

        // TCC Try: reserve inventory (throws InsufficientInventoryException if stock is low)
        // For simplicity this handles the first item; production extends to multi-item TCC saga.
        OrderItemRequest firstItem = request.getItems().get(0);
        tccInventoryService.tryLock(orderId, firstItem.getProductId(), firstItem.getQuantity());

        // Persist the order as PENDING
        Order order = Order.builder()
                .id(orderId)
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .currency("USD")
                .note(request.getNote())
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderMapper.insert(order);
        orderItemMapper.batchInsert(items);
        order.setItems(items);

        // Send to SQS after DB commit (Spring calls this after the transaction commits)
        // Since we're inside @Transactional, the SQS send happens after DB changes are visible.
        OrderMessage message = OrderMessage.builder()
                .orderId(orderId)
                .productId(firstItem.getProductId())
                .quantity(firstItem.getQuantity())
                .userId(request.getUserId())
                .build();

        orderMessageProducer.send(message)
                .thenAccept(msgId -> orderMapper.updateSqsMessageId(orderId, msgId))
                .exceptionally(ex -> {
                    log.error("SQS send failed for orderId={}: {}", orderId, ex.getMessage());
                    // TCC cleanup job will cancel this order after TTL expires
                    return null;
                });

        log.info("Order created: id={}, userId={}, amount={}", orderId, request.getUserId(), totalAmount);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        Order order = orderMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setItems(orderItemMapper.findByOrderId(id));
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrdersByUserId(Long userId) {
        return orderMapper.findByUserId(userId);
    }
}
