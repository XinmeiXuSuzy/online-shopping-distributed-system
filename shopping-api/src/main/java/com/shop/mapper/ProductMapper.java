package com.shop.mapper;

import com.shop.domain.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductMapper {

    void insert(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    List<Product> findByCategory(@Param("category") String category);

    int update(Product product);

    int softDelete(Long id);
}
