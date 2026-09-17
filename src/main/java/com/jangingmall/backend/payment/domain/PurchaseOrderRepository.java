package com.jangingmall.backend.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository {
    PurchaseOrder save(PurchaseOrder order);
    Optional<PurchaseOrder> findById(Long id);
    Optional<PurchaseOrder> findByIdForUpdate(Long id);
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
    Optional<PurchaseOrder> findByOrderNumberForUpdate(String orderNumber);
    Optional<PurchaseOrder> findByMemberIdAndClientRequestKey(Long memberId, String clientRequestKey);
    List<PurchaseOrder> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(OrderStatus status, Instant cutoff);
}
