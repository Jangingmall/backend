package com.jangingmall.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_method", uniqueConstraints = @UniqueConstraint(
    name = "uk_payment_method_member_fingerprint", columnNames = {"member_id", "card_fingerprint"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod type;

    @Column(name = "card_company", nullable = false, length = 50)
    private String cardCompany;

    @Column(name = "card_number_masked", nullable = false, length = 30)
    private String cardNumberMasked;

    @Column(name = "card_fingerprint", nullable = false, length = 64)
    private String cardFingerprint;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SavedPaymentMethod(Long memberId, PaymentMethod type, String cardCompany, String cardNumberMasked,
                              String cardFingerprint, boolean defaultMethod) {
        this.memberId = memberId;
        this.type = type;
        this.cardCompany = cardCompany;
        this.cardNumberMasked = cardNumberMasked;
        this.cardFingerprint = cardFingerprint;
        this.defaultMethod = defaultMethod;
        this.createdAt = Instant.now();
    }

    public void makeDefault() {
        defaultMethod = true;
    }
}
