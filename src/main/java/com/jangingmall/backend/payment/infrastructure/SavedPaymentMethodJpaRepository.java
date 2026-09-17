package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.SavedPaymentMethod;
import com.jangingmall.backend.payment.domain.SavedPaymentMethodRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedPaymentMethodJpaRepository
    extends JpaRepository<SavedPaymentMethod, Long>, SavedPaymentMethodRepository {
}
