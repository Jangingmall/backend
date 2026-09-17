package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrder, Long>, PurchaseOrderRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :orderId")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("orderId") Long orderId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.orderNumber = :orderNumber")
    Optional<PurchaseOrder> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PurchaseOrder> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
        com.jangingmall.backend.payment.domain.OrderStatus status,
        Instant cutoff
    );
}
