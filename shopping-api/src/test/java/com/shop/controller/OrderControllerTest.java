package com.shop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.domain.Order;
import com.shop.domain.enums.OrderStatus;
import com.shop.dto.request.CreateOrderRequest;
import com.shop.dto.request.OrderItemRequest;
import com.shop.exception.InsufficientInventoryException;
import com.shop.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  OrderService orderService;

    @Test
    void createOrder_returns202_withPendingStatus() throws Exception {
        Order pending = Order.builder()
                .id(1001L)
                .userId(1L)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("99.99"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.createOrder(any())).thenReturn(pending);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(202))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.id").value(1001));
    }

    @Test
    void createOrder_returns409_whenInsufficientInventory() throws Exception {
        when(orderService.createOrder(any()))
                .thenThrow(new InsufficientInventoryException(10L, 100));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(10L);
        item.setQuantity(100);
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void createOrder_returns400_whenItemsMissing() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        // items is null — validation should reject

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_returns200_withOrderData() throws Exception {
        Order order = Order.builder()
                .id(1001L)
                .userId(1L)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("99.99"))
                .currency("USD")
                .createdAt(LocalDateTime.now())
                .items(List.of())
                .build();

        when(orderService.getOrderById(1001L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.totalAmount").value(99.99));
    }
}
