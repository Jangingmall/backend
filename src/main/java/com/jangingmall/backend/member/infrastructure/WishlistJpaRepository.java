package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;

interface WishlistJpaRepository extends JpaRepository<Wishlist, Long> {
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);
    void deleteByMemberIdAndProductId(Long memberId, Long productId);
}
