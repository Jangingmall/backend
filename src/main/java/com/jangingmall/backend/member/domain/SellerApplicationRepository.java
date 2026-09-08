package com.jangingmall.backend.member.domain;

import java.util.Optional;

public interface SellerApplicationRepository {
    SellerApplication save(SellerApplication application);
    Optional<SellerApplication> findById(Long id);
    Optional<SellerApplication> findFirstByMemberIdOrderByIdDesc(Long memberId);
    boolean existsByMemberIdAndStatus(Long memberId, SellerApplication.Status status);
    void flush();
}
