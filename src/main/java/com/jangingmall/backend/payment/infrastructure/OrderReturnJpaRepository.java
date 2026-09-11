package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.OrderReturnRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReturnJpaRepository extends JpaRepository<OrderReturn, Long>, OrderReturnRepository {}
