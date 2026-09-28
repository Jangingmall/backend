package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.OrderReturnRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReturnJpaRepository extends JpaRepository<OrderReturn, Long>, OrderReturnRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OrderReturn r WHERE r.id = :id")
    Optional<OrderReturn> findByIdForUpdate(@Param("id") Long id);
}
