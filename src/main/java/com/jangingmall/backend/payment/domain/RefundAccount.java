package com.jangingmall.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refund_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_account_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "account_number_masked", nullable = false, length = 30)
    private String accountNumberMasked;

    @Column(name = "account_fingerprint", nullable = false, length = 64)
    private String accountFingerprint;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RefundAccount(Long memberId, String bankName, String accountNumberMasked, String accountFingerprint,
                         String accountHolder) {
        this.memberId = memberId;
        this.bankName = bankName;
        this.accountNumberMasked = accountNumberMasked;
        this.accountFingerprint = accountFingerprint;
        this.accountHolder = accountHolder;
        this.createdAt = Instant.now();
    }
}
