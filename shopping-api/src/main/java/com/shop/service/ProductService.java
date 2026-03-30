package com.shop.service;

import com.shop.domain.Product;
import com.shop.dto.request.CreateProductRequest;

import java.util.List;

public interface ProductService {

    Product createProduct(CreateProductRequest request);

    Product getProductById(Long id);

    List<Product> getAllProducts();

    List<Product> getProductsByCategory(String category);

    Product updateProduct(Long id, CreateProductRequest request);

    void deleteProduct(Long id);
}
