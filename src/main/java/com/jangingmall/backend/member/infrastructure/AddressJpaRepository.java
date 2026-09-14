package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.Address;
import com.jangingmall.backend.member.domain.AddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressJpaRepository extends JpaRepository<Address, Long>, AddressRepository {}
