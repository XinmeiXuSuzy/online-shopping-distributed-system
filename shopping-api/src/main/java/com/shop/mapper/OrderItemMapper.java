package com.shop.mapper;

import com.shop.domain.OrderItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    void batchInsert(List<OrderItem> items);

    List<OrderItem> findByOrderId(Long orderId);
}
