package com.jangingmall.backend.member.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberActivityRepository {
    Optional<MemberSettings> settings(Long memberId);
    MemberSettings saveSettings(MemberSettings settings);
    void recordView(Long memberId, Long productId, LocalDateTime viewedAt);
    void clearViews(Long memberId);
    void subscribe(Long memberId, Long artisanId);
    void unsubscribe(Long memberId, Long artisanId);
    void notifications(Long memberId, boolean enabled);
}
