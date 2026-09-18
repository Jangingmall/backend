package com.jangingmall.backend.payment.domain;

public enum OrderStatus {
    CREATED,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    IN_DELIVERY,
    DELIVERED,
    RETURN_REQUESTED
}
