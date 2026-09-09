package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.MemberRole;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class MemberQueryService {
    private final MemberAccess access;
    private final MemberReadRepository reads;

    @Transactional(readOnly = true)
    public CursorPage<Map<String, Object>> wishes(Long memberId, PageRequest page) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.wishes(memberId, page);
    }

    @Transactional(readOnly = true)
    public CursorPage<Map<String, Object>> orders(Long memberId, PageRequest page, String status) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.orders(memberId, page, status);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> order(Long memberId, Long orderId) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.order(memberId, orderId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CursorPage<Map<String, Object>> reviews(Long memberId, PageRequest page, boolean writable) {
        access.requireRole(memberId, MemberRole.USER);
        return reads.reviews(memberId, page, writable);
    }
}
