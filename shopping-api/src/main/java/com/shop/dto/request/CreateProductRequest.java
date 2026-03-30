package com.shop.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProductRequest {

    @NotBlank
    @Size(max = 256)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    @Size(max = 64)
    private String category;

    @Size(max = 512)
    private String imageUrl;
}
