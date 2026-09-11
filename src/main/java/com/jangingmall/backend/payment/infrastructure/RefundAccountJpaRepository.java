package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.RefundAccount;
import com.jangingmall.backend.payment.domain.RefundAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundAccountJpaRepository extends JpaRepository<RefundAccount, Long>, RefundAccountRepository {
}
