package com.jangingmall.backend.payment.application;

import java.util.Optional;

public interface PaymentGateway {
    void confirm(String paymentKey, String orderNumber, long amount);
    void cancel(String paymentKey, String reason);

    /** 웹훅 본문을 신뢰하지 않고 토스 서버에서 결제 상태를 다시 조회한다. */
    PaymentSnapshot find(String paymentKey);

    /** 브라우저의 성공/실패 리다이렉트 대신 주문번호로 토스의 실제 상태를 확인한다. */
    Optional<PaymentSnapshot> findByOrderNumber(String orderNumber);

    record PaymentSnapshot(String paymentKey, String orderNumber, long totalAmount, String status) {}
}
