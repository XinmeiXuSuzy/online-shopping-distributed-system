package com.shop.service;

import com.shop.domain.Product;
import com.shop.dto.request.CreateProductRequest;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.ProductMapper;
import com.shop.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Override
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Product product = Product.builder()
                .id(idGenerator.nextId())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .status(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productMapper.insert(product);
        log.info("Created product id={}", product.getId());
        return product;
    }

    @Override
    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productMapper.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        return productMapper.findByCategory(category);
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, CreateProductRequest request) {
        Product existing = getProductById(id);
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setCategory(request.getCategory());
        existing.setImageUrl(request.getImageUrl());
        existing.setUpdatedAt(LocalDateTime.now());
        productMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        getProductById(id); // verify existence
        productMapper.softDelete(id);
    }
}
