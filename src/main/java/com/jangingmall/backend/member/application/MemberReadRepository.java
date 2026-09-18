package com.jangingmall.backend.member.application;

import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Read projections contain IDs and values, never another domain's JPA entities. */
public interface MemberReadRepository {
    Page<SellerApplicationData> applications(Pageable pageable, String status);
    Page<Map<String, Object>> wishes(Long memberId, Pageable pageable);
    Page<Map<String, Object>> orders(Long memberId, Pageable pageable, String status, String from, String to, String artisanName);
    Optional<Map<String, Object>> order(Long memberId, Long orderId);
    Map<String, Object> orderSummary(Long memberId);
    Page<Map<String, Object>> reviews(Long memberId, Pageable pageable, boolean writable);
    Page<Map<String, Object>> recentViews(Long memberId, Pageable pageable);
    Page<Map<String, Object>> artisans(Pageable pageable, String certification, String category, String initial, String sort);
    Optional<Map<String, Object>> artisan(Long artisanId);
    Page<Map<String, Object>> subscriptions(Long memberId, Pageable pageable);
    boolean productVisible(Long productId);
    boolean categoryExists(String category);
}
