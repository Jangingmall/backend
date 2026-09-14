package com.jangingmall.backend.member.domain;

import java.util.List;
import java.util.Optional;

public interface AddressRepository {
    List<Address> findByMemberIdOrderByIdAsc(Long memberId);
    Optional<Address> findById(Long id);
    Address save(Address address);
    void delete(Address address);
    void flush();
}
