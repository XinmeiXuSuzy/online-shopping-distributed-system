package com.shop.service;

import com.shop.domain.Inventory;
import com.shop.domain.TccTransaction;
import com.shop.domain.enums.OrderStatus;
import com.shop.domain.enums.TccStatus;
import com.shop.exception.InsufficientInventoryException;
import com.shop.mapper.InventoryMapper;
import com.shop.mapper.OrderMapper;
import com.shop.mapper.TccTransactionMapper;
import com.shop.service.tcc.TccInventoryServiceImpl;
import com.shop.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TccInventoryServiceTest {

    @Mock private InventoryMapper inventoryMapper;
    @Mock private TccTransactionMapper tccTransactionMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private SnowflakeIdGenerator idGenerator;

    @InjectMocks
    private TccInventoryServiceImpl tccInventoryService;

    @BeforeEach
    void setUp() {
        when(idGenerator.nextId()).thenReturn(99L);
    }

    // --- Try phase ---

    @Test
    void tryLock_success_whenSufficientStock() {
        Long orderId = 1L;
        Long productId = 10L;
        int qty = 5;

        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryMapper.tryLockStock(productId, qty)).thenReturn(1);

        tccInventoryService.tryLock(orderId, productId, qty);

        verify(tccTransactionMapper).insert(argThat(tcc ->
                tcc.getOrderId().equals(orderId)
                && tcc.getStatus() == TccStatus.TRYING));
        verify(inventoryMapper).tryLockStock(productId, qty);
    }

    @Test
    void tryLock_throwsInsufficientInventory_whenNoStock() {
        Long orderId = 2L;
        Long productId = 10L;
        int qty = 999;

        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(inventoryMapper.tryLockStock(productId, qty)).thenReturn(0); // 0 rows = oversell guard hit

        assertThatThrownBy(() -> tccInventoryService.tryLock(orderId, productId, qty))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void tryLock_isIdempotent_whenAlreadyTrying() {
        Long orderId = 3L;
        TccTransaction existing = TccTransaction.builder()
                .orderId(orderId).status(TccStatus.TRYING).build();
        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        tccInventoryService.tryLock(orderId, 10L, 1);

        // Should NOT insert a new record or touch inventory
        verify(tccTransactionMapper, never()).insert(any());
        verify(inventoryMapper, never()).tryLockStock(anyLong(), anyInt());
    }

    // --- Confirm phase ---

    @Test
    void confirm_success() {
        Long orderId = 4L;
        TccTransaction tcc = TccTransaction.builder()
                .orderId(orderId).productId(10L).quantity(3).status(TccStatus.TRYING).build();
        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.of(tcc));

        tccInventoryService.confirm(orderId);

        verify(inventoryMapper).confirmStock(10L, 3);
        verify(tccTransactionMapper).updateStatus(eq(orderId), eq(TccStatus.CONFIRMED),
                any(LocalDateTime.class), isNull(), isNull());
        verify(orderMapper).updateStatus(orderId, OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_isIdempotent_whenAlreadyConfirmed() {
        Long orderId = 5L;
        TccTransaction tcc = TccTransaction.builder()
                .orderId(orderId).productId(10L).quantity(3).status(TccStatus.CONFIRMED).build();
        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.of(tcc));

        tccInventoryService.confirm(orderId);

        verify(inventoryMapper, never()).confirmStock(anyLong(), anyInt());
    }

    // --- Cancel phase ---

    @Test
    void cancel_success_returnsStockToAvailable() {
        Long orderId = 6L;
        TccTransaction tcc = TccTransaction.builder()
                .orderId(orderId).productId(10L).quantity(2).status(TccStatus.TRYING).build();
        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.of(tcc));

        tccInventoryService.cancel(orderId, "payment failed");

        verify(inventoryMapper).cancelLockStock(10L, 2);
        verify(tccTransactionMapper).updateStatus(eq(orderId), eq(TccStatus.CANCELLED),
                isNull(), any(LocalDateTime.class), eq("payment failed"));
        verify(orderMapper).updateStatus(orderId, OrderStatus.CANCELLED);
    }

    @Test
    void cancel_isIdempotent_whenAlreadyCancelled() {
        Long orderId = 7L;
        TccTransaction tcc = TccTransaction.builder()
                .orderId(orderId).status(TccStatus.CANCELLED).build();
        when(tccTransactionMapper.findByOrderId(orderId)).thenReturn(Optional.of(tcc));

        tccInventoryService.cancel(orderId, "duplicate cancel");

        verify(inventoryMapper, never()).cancelLockStock(anyLong(), anyInt());
    }
}
