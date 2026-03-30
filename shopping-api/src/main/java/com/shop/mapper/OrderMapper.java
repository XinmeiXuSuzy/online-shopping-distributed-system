package com.shop.mapper;

import com.shop.domain.Order;
import com.shop.domain.enums.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrderMapper {

    void insert(Order order);

    Optional<Order> findById(Long id);

    List<Order> findByUserId(Long userId);

    List<Order> findByStatus(OrderStatus status);

    int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);

    int updateSqsMessageId(@Param("id") Long id, @Param("sqsMessageId") String sqsMessageId);
}
