package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.ArtisanSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ArtisanSubscriptionJpaRepository extends JpaRepository<ArtisanSubscription, Long> {
    boolean existsByMemberIdAndArtisanId(Long memberId, Long artisanId);
    void deleteByMemberIdAndArtisanId(Long memberId, Long artisanId);
    List<ArtisanSubscription> findAllByMemberId(Long memberId);
}
