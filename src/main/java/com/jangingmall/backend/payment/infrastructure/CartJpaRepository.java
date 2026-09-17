package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.Cart;
import com.jangingmall.backend.payment.domain.CartRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartJpaRepository extends JpaRepository<Cart, Long>, CartRepository {}
