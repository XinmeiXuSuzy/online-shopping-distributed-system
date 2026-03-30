package com.shop.controller;

import com.shop.domain.Order;
import com.shop.domain.OrderItem;
import com.shop.dto.request.CreateOrderRequest;
import com.shop.dto.response.ApiResponse;
import com.shop.dto.response.OrderResponse;
import com.shop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates a flash-sale order. Returns 202 Accepted with status=PENDING.
     * The order is processed asynchronously via SQS → TCC confirm/cancel.
     * Poll GET /api/v1/orders/{id} to check final status.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.<OrderResponse>builder()
                .code(202)
                .message("Order accepted for processing")
                .data(toResponse(orderService.createOrder(request)))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id) {
        return ApiResponse.ok(toResponse(orderService.getOrderById(id)));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderResponse>> getUserOrders(@PathVariable Long userId) {
        List<OrderResponse> orders = orderService.getOrdersByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.ok(orders);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> items = null;
        if (order.getItems() != null) {
            items = order.getItems().stream()
                    .map(this::toItemResponse)
                    .collect(Collectors.toList());
        }
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .note(order.getNote())
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderResponse.OrderItemResponse toItemResponse(OrderItem item) {
        return OrderResponse.OrderItemResponse.builder()
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
