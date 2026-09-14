package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class MemberActivityService {
    private final MemberAccess access;
    private final MemberActivityRepository activities;
    private final MemberReadRepository reads;

    @Transactional(readOnly = true)
    public Settings settings(Long memberId) {
        var member = access.active(memberId);
        return Settings.from(activities.settings(memberId).orElseGet(() -> new MemberSettings(memberId, member.isMarketingAgreed())));
    }

    @Transactional
    public Settings updateSettings(Long memberId, Optional<Boolean> darkMode, Optional<Boolean> marketing) {
        var member = access.lock(memberId);
        var settings = activities.settings(memberId).orElseGet(() -> new MemberSettings(memberId, member.isMarketingAgreed()));
        settings.update(darkMode, marketing);
        marketing.ifPresent(member::agreeMarketing);
        return Settings.from(activities.saveSettings(settings));
    }

    @Transactional
    public void recordView(Long memberId, Long productId) {
        access.lock(memberId);
        requireProduct(productId);
        activities.recordView(memberId, productId, LocalDateTime.now());
    }

    @Transactional
    public void mergeViews(Long memberId, List<Long> productIds) {
        access.lock(memberId);
        List<Long> distinct = productIds.stream().distinct().toList();
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < distinct.size(); index++) {
            recordVisible(memberId, distinct.get(index), now.minusNanos(index * 1000L));
        }
    }

    @Transactional
    public void clearViews(Long memberId) {
        access.lock(memberId);
        activities.clearViews(memberId);
    }

    @Transactional(readOnly = true)
    public CursorPage<Map<String, Object>> recentViews(Long memberId, String cursor, int limit) {
        access.active(memberId);
        return reads.recentViews(memberId, cursor, limit);
    }

    @Transactional
    public void subscribe(Long memberId, Long artisanId) {
        access.lockRole(memberId, MemberRole.USER);
        reads.artisan(artisanId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        activities.subscribe(memberId, artisanId);
    }

    @Transactional
    public void unsubscribe(Long memberId, Long artisanId) {
        access.lockRole(memberId, MemberRole.USER);
        activities.unsubscribe(memberId, artisanId);
    }

    @Transactional
    public void notifications(Long memberId, boolean enabled) {
        access.lockRole(memberId, MemberRole.USER);
        activities.notifications(memberId, enabled);
    }

    @Transactional(readOnly = true)
    public CursorPage<Map<String, Object>> subscriptions(Long memberId, PageRequest page) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.subscriptions(memberId, page);
    }

    private void recordVisible(Long memberId, Long productId, LocalDateTime viewedAt) {
        if (reads.productVisible(productId)) {
            activities.recordView(memberId, productId, viewedAt);
        }
    }

    private void requireProduct(Long productId) {
        if (!reads.productVisible(productId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
    }

    public record Settings(boolean darkMode, boolean marketing) {
        public static Settings from(MemberSettings settings) {
            return new Settings(settings.isDarkMode(), settings.isMarketing());
        }
    }
}
