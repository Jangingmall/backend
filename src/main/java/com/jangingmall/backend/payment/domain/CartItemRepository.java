package com.jangingmall.backend.payment.domain;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {
    CartItem save(CartItem item);
    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);
    Optional<CartItem> findById(Long id);
    void delete(CartItem item);
    void deleteByCartId(Long cartId);
}
