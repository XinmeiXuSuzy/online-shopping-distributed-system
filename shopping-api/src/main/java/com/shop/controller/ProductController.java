package com.shop.controller;

import com.shop.domain.Product;
import com.shop.dto.request.CreateProductRequest;
import com.shop.dto.response.ApiResponse;
import com.shop.dto.response.ProductResponse;
import com.shop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.created(toResponse(productService.createProduct(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.ok(toResponse(productService.getProductById(id)));
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> getAllProducts(
            @RequestParam(required = false) String category) {
        List<Product> products = category != null
                ? productService.getProductsByCategory(category)
                : productService.getAllProducts();
        return ApiResponse.ok(products.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(@PathVariable Long id,
                                                      @Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(toResponse(productService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.ok(null);
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .category(p.getCategory())
                .imageUrl(p.getImageUrl())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
