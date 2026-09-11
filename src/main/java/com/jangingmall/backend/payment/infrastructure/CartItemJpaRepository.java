package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.CartItem;
import com.jangingmall.backend.payment.domain.CartItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long>, CartItemRepository {}
