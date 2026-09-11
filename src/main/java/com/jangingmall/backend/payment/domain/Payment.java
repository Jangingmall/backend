package com.jangingmall.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "payment_key", unique = true, length = 255)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(Long orderId, long amount, PaymentMethod method) {
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.READY;
        this.updatedAt = Instant.now();
    }

    public boolean isSameConfirmation(String paymentKey) {
        return status == PaymentStatus.DONE && Objects.equals(this.paymentKey, paymentKey);
    }

    public void approve(String paymentKey) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("결제 준비 상태가 아닙니다.");
        }
        this.paymentKey = paymentKey;
        status = PaymentStatus.DONE;
        approvedAt = Instant.now();
        updatedAt = approvedAt;
    }

    public void fail(String errorCode, String errorMessage) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("결제 준비 상태가 아닙니다.");
        }
        failureCode = errorCode;
        failureMessage = errorMessage;
        status = PaymentStatus.FAILED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status != PaymentStatus.DONE) {
            throw new IllegalStateException("결제 완료 건만 취소할 수 있습니다.");
        }
        status = PaymentStatus.CANCELED;
        canceledAt = Instant.now();
        updatedAt = canceledAt;
    }
}
