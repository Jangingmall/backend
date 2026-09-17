package com.jangingmall.backend.payment.domain;

import java.util.Optional;

public interface OrderReturnRepository {
    OrderReturn save(OrderReturn orderReturn);
    Optional<OrderReturn> findByOrderId(Long orderId);
}
