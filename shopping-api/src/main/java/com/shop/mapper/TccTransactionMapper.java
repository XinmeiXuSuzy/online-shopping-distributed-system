package com.shop.mapper;

import com.shop.domain.TccTransaction;
import com.shop.domain.enums.TccStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface TccTransactionMapper {

    void insert(TccTransaction tccTransaction);

    Optional<TccTransaction> findByOrderId(Long orderId);

    int updateStatus(@Param("orderId") Long orderId,
                     @Param("status") TccStatus status,
                     @Param("confirmedAt") LocalDateTime confirmedAt,
                     @Param("cancelledAt") LocalDateTime cancelledAt,
                     @Param("cancelReason") String cancelReason);

    /**
     * Used by the background cleanup job to find expired TRYING transactions.
     */
    List<TccTransaction> findExpiredTrying(@Param("now") LocalDateTime now);
}
