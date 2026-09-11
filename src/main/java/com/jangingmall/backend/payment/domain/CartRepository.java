package com.jangingmall.backend.payment.domain;

import java.util.Optional;

public interface CartRepository {
    Cart save(Cart cart);
    Optional<Cart> findByMemberId(Long memberId);
    Optional<Cart> findByGuestCartId(String guestCartId);
    void delete(Cart cart);
}
