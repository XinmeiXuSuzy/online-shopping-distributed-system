package com.shop.controller;

import com.shop.domain.Inventory;
import com.shop.dto.request.UpdateInventoryRequest;
import com.shop.dto.response.ApiResponse;
import com.shop.dto.response.InventoryResponse;
import com.shop.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/product/{productId}/init")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryResponse> initInventory(@PathVariable Long productId,
                                                        @Valid @RequestBody UpdateInventoryRequest request) {
        return ApiResponse.created(toResponse(inventoryService.initInventory(productId, request.getTotalStock())));
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<InventoryResponse> getInventory(@PathVariable Long productId) {
        return ApiResponse.ok(toResponse(inventoryService.getByProductId(productId)));
    }

    @PutMapping("/product/{productId}/stock")
    public ApiResponse<InventoryResponse> updateStock(@PathVariable Long productId,
                                                      @Valid @RequestBody UpdateInventoryRequest request) {
        return ApiResponse.ok(toResponse(inventoryService.updateStock(productId, request.getTotalStock())));
    }

    @PostMapping("/product/{productId}/replenish")
    public ApiResponse<InventoryResponse> replenish(@PathVariable Long productId,
                                                    @Valid @RequestBody UpdateInventoryRequest request) {
        return ApiResponse.ok(toResponse(inventoryService.replenish(productId, request.getTotalStock())));
    }

    private InventoryResponse toResponse(Inventory inv) {
        return InventoryResponse.builder()
                .productId(inv.getProductId())
                .totalStock(inv.getTotalStock())
                .availableStock(inv.getAvailableStock())
                .lockedQuantity(inv.getLockedQuantity())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}
