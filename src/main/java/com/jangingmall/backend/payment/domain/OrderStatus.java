package com.jangingmall.backend.payment.domain;

public enum OrderStatus {
    CREATED,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    DELIVERED,
    RETURN_REQUESTED
}
