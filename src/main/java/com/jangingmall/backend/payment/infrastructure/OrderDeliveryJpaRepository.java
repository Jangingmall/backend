package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.OrderDelivery;
import com.jangingmall.backend.payment.domain.OrderDeliveryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDeliveryJpaRepository extends JpaRepository<OrderDelivery, Long>, OrderDeliveryRepository {}
