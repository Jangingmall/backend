package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerApplicationJpaRepository extends JpaRepository<SellerApplication, Long>, SellerApplicationRepository {}
