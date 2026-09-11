package com.jangingmall.backend.payment.domain;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(Long paymentId);
    Optional<Payment> findByOrderId(Long orderId);
    Optional<Payment> findByPaymentKey(String paymentKey);
    Optional<Payment> findByOrderIdForUpdate(Long orderId);
    Optional<Payment> findByIdForUpdate(Long paymentId);
}
