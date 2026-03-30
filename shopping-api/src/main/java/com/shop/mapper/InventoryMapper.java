package com.shop.mapper;

import com.shop.domain.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface InventoryMapper {

    void insert(Inventory inventory);

    Optional<Inventory> findByProductId(Long productId);

    /**
     * Atomically locks qty units: decrements available_stock and increments locked_quantity.
     * The WHERE clause guards against overselling:
     *   WHERE product_id = #{productId} AND available_stock >= #{quantity}
     *
     * @return number of affected rows (1 = success, 0 = insufficient stock)
     */
    int tryLockStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Confirm: decrements total_stock and locked_quantity (completes the sale).
     */
    int confirmStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Cancel: returns locked units back to available_stock.
     */
    int cancelLockStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Direct replenishment (admin use): sets total_stock and recalculates available_stock.
     */
    int replenish(@Param("productId") Long productId, @Param("addedStock") int addedStock);

    int updateTotalStock(@Param("productId") Long productId, @Param("totalStock") int totalStock);
}
