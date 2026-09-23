package com.jangingmall.backend.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderReturnRepository {
    OrderReturn save(OrderReturn orderReturn);
    Optional<OrderReturn> findByOrderId(Long orderId);
    List<OrderReturn> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(ReturnStatus status, Instant before);
}
