package com.jangingmall.backend.payment.domain;

import java.util.Optional;

public interface OrderDeliveryRepository {
    OrderDelivery save(OrderDelivery delivery);
    Optional<OrderDelivery> findByOrderId(Long orderId);
}
