package com.jangingmall.backend.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MemberStatus status;

    @Column(name = "age14_or_older", nullable = false)
    private boolean age14OrOlder;

    @Column(name = "terms_agreed", nullable = false)
    private boolean termsAgreed;

    @Column(name = "privacy_agreed", nullable = false)
    private boolean privacyAgreed;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Member(
        String email,
        String passwordHash,
        String name,
        String phone,
        MemberRole role,
        boolean age14OrOlder,
        boolean termsAgreed,
        boolean privacyAgreed,
        boolean marketingAgreed
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.status = MemberStatus.PENDING_VERIFICATION;
        this.age14OrOlder = age14OrOlder;
        this.termsAgreed = termsAgreed;
        this.privacyAgreed = privacyAgreed;
        this.marketingAgreed = marketingAgreed;
    }

    public static Member register(
        String email,
        String passwordHash,
        String name,
        String phone,
        MemberRole role,
        boolean age14OrOlder,
        boolean termsAgreed,
        boolean privacyAgreed,
        boolean marketingAgreed
    ) {
        return new Member(
            normalizeEmail(email),
            passwordHash,
            name,
            phone,
            role,
            age14OrOlder,
            termsAgreed,
            privacyAgreed,
            marketingAgreed
        );
    }

    public void activate() {
        if (status == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("Withdrawn member cannot be activated.");
        }
        status = MemberStatus.ACTIVE;
    }

    public boolean canLogIn() {
        return status == MemberStatus.ACTIVE && deletedAt == null;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
