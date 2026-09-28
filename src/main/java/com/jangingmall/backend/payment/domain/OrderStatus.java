package com.jangingmall.backend.payment.domain;

public enum OrderStatus {
    CREATED,
    PAID,
    PAYMENT_FAILED,
    EXPIRED,
    CANCELED,
    IN_DELIVERY,
    DELIVERED,
    PURCHASE_CONFIRMED,
    RETURN_REQUESTED
}
