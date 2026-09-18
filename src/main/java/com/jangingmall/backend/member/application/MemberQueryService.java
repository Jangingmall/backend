package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.MemberActivityRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class MemberQueryService {
    private final MemberAccess access;
    private final MemberReadRepository reads;
    private final MemberActivityRepository activities;

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> wishes(Long memberId, Pageable pageable) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.wishes(memberId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> orders(Long memberId, Pageable pageable, String status,
            String from, String to, String artisanName) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.orders(memberId, pageable, status, from, to, artisanName);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> order(Long memberId, Long orderId) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.order(memberId, orderId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> orderCountSummary(Long memberId) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.orderSummary(memberId);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> reviews(Long memberId, Pageable pageable, boolean writable) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.reviews(memberId, pageable, writable);
    }

    @Transactional(readOnly = true)
    public boolean isWished(Long memberId, Long productId) {
        access.requireRole(memberId, MemberRole.USER);
        return activities.isWished(memberId, productId);
    }
}
