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
@Table(name = "cart")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    @Column(name = "member_id", unique = true)
    private Long memberId;

    @Column(name = "guest_cart_id", unique = true, length = 50)
    private String guestCartId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Cart(Long memberId, String guestCartId) {
        this.memberId = memberId;
        this.guestCartId = guestCartId;
        this.updatedAt = Instant.now();
    }

    public static Cart member(Long memberId) {
        return new Cart(memberId, null);
    }

    public static Cart guest(String guestCartId) {
        return new Cart(null, guestCartId);
    }

    public void touch() {
        updatedAt = Instant.now();
    }
}
