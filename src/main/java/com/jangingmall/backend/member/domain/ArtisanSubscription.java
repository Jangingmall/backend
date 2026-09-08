package com.jangingmall.backend.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "artisan_subscription", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "artisan_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtisanSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "artisan_id", nullable = false)
    private Long artisanId;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
