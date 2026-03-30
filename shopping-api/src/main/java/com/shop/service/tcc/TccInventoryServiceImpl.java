package com.shop.service.tcc;

import com.shop.domain.TccTransaction;
import com.shop.domain.enums.TccStatus;
import com.shop.exception.InsufficientInventoryException;
import com.shop.mapper.InventoryMapper;
import com.shop.mapper.OrderMapper;
import com.shop.mapper.TccTransactionMapper;
import com.shop.domain.enums.OrderStatus;
import com.shop.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TccInventoryServiceImpl implements TccInventoryService {

    private final InventoryMapper inventoryMapper;
    private final TccTransactionMapper tccTransactionMapper;
    private final OrderMapper orderMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Value("${app.tcc.try-expire-minutes:5}")
    private int tryExpireMinutes;

    @Override
    @Transactional
    public void tryLock(Long orderId, Long productId, int quantity) {
        // Idempotency: if a TRYING record already exists for this order, skip
        Optional<TccTransaction> existing = tccTransactionMapper.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.warn("TCC tryLock skipped — already exists for orderId={}", orderId);
            return;
        }

        // Create the TCC journal entry first
        TccTransaction tcc = TccTransaction.builder()
                .id(idGenerator.nextId())
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status(TccStatus.TRYING)
                .tryExpireAt(LocalDateTime.now().plusMinutes(tryExpireMinutes))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        tccTransactionMapper.insert(tcc);

        // Atomically lock inventory — the WHERE clause prevents overselling
        int affected = inventoryMapper.tryLockStock(productId, quantity);
        if (affected == 0) {
            // The conditional UPDATE returned 0 rows: stock is insufficient
            // Throw here; Spring @Transactional will roll back the TCC insert
            throw new InsufficientInventoryException(productId, quantity);
        }

        log.info("TCC try: orderId={}, productId={}, qty={}", orderId, productId, quantity);
    }

    @Override
    @Transactional
    public void confirm(Long orderId) {
        TccTransaction tcc = tccTransactionMapper.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("TCC record not found for orderId=" + orderId));

        if (tcc.getStatus() == TccStatus.CONFIRMED) {
            log.warn("TCC confirm skipped — already confirmed for orderId={}", orderId);
            return;
        }
        if (tcc.getStatus() == TccStatus.CANCELLED) {
            log.warn("TCC confirm refused — already cancelled for orderId={}", orderId);
            return;
        }

        inventoryMapper.confirmStock(tcc.getProductId(), tcc.getQuantity());
        tccTransactionMapper.updateStatus(orderId, TccStatus.CONFIRMED, LocalDateTime.now(), null, null);
        orderMapper.updateStatus(orderId, OrderStatus.CONFIRMED);

        log.info("TCC confirm: orderId={}, productId={}, qty={}", orderId, tcc.getProductId(), tcc.getQuantity());
    }

    @Override
    @Transactional
    public void cancel(Long orderId, String reason) {
        TccTransaction tcc = tccTransactionMapper.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("TCC record not found for orderId=" + orderId));

        if (tcc.getStatus() == TccStatus.CANCELLED) {
            log.warn("TCC cancel skipped — already cancelled for orderId={}", orderId);
            return;
        }
        if (tcc.getStatus() == TccStatus.CONFIRMED) {
            log.error("TCC cancel refused — already confirmed for orderId={}", orderId);
            return;
        }

        inventoryMapper.cancelLockStock(tcc.getProductId(), tcc.getQuantity());
        tccTransactionMapper.updateStatus(orderId, TccStatus.CANCELLED, null, LocalDateTime.now(), reason);
        orderMapper.updateStatus(orderId, OrderStatus.CANCELLED);

        log.info("TCC cancel: orderId={}, productId={}, qty={}, reason={}",
                orderId, tcc.getProductId(), tcc.getQuantity(), reason);
    }

    /**
     * Background cleanup: auto-cancels TRYING transactions that have exceeded their TTL.
     * Runs every minute. Guards against SQS consumer crashes that leave reservations open.
     */
    @Scheduled(fixedDelayString = "${app.tcc.cleanup-interval-ms:60000}")
    @Transactional
    public void cleanupExpiredTransactions() {
        List<TccTransaction> expired = tccTransactionMapper.findExpiredTrying(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        log.warn("TCC cleanup: found {} expired TRYING transactions", expired.size());
        for (TccTransaction tcc : expired) {
            try {
                cancel(tcc.getOrderId(), "TCC try TTL expired — auto-cancelled by cleanup job");
            } catch (Exception e) {
                log.error("TCC cleanup failed for orderId={}: {}", tcc.getOrderId(), e.getMessage());
            }
        }
    }
}
