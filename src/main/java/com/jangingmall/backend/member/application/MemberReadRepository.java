package com.jangingmall.backend.member.application;

import java.util.Map;
import java.util.Optional;

/** Read projections contain IDs and values, never another domain's JPA entities. */
public interface MemberReadRepository {
    CursorPage<SellerApplicationData> applications(PageRequest page, String status);
    CursorPage<Map<String, Object>> wishes(Long memberId, PageRequest page);
    CursorPage<Map<String, Object>> orders(Long memberId, PageRequest page, String status);
    Optional<Map<String, Object>> order(Long memberId, Long orderId);
    CursorPage<Map<String, Object>> reviews(Long memberId, PageRequest page, boolean writable);
    CursorPage<Map<String, Object>> recentViews(Long memberId, String cursor, int limit);
    CursorPage<Map<String, Object>> artisans(String cursor, int limit, String certification, String category, String initial, String sort);
    Optional<Map<String, Object>> artisan(Long artisanId);
    CursorPage<Map<String, Object>> subscriptions(Long memberId, PageRequest page);
    boolean productVisible(Long productId);
    boolean categoryExists(String category);
}
