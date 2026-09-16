package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.RecentView;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecentViewJpaRepository extends JpaRepository<RecentView, Long> {
    Optional<RecentView> findByMemberIdAndProductId(Long memberId, Long productId);
    void deleteByMemberId(Long memberId);
}
