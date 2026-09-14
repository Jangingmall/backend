package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberAccess {
    private final MemberRepository members;

    public Member active(Long memberId) {
        return requireActive(members.findById(memberId)
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED)));
    }

    public Member lock(Long memberId) {
        return requireActive(members.findByIdForUpdate(memberId)
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED)));
    }

    public Member requireRole(Long memberId, MemberRole role) {
        Member member = active(memberId);
        checkRole(member, role);
        return member;
    }

    public Member lockRole(Long memberId, MemberRole role) {
        Member member = lock(memberId);
        checkRole(member, role);
        return member;
    }

    public Member lockExactRole(Long memberId, MemberRole role) {
        Member member = lock(memberId);
        if (member.getRole() != role) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        return member;
    }

    private Member requireActive(Member member) {
        if (!member.canLogIn()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        return member;
    }

    private void checkRole(Member member, MemberRole role) {
        if (!member.getRole().allows(role)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
    }
}
