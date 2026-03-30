package com.shop.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotNull
    private Long userId;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    private String note;
}
