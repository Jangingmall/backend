package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberStatus;

public record MemberSignupResult(
    Long memberId,
    String email,
    String name,
    String nickname,
    MemberRole role,
    String profileImageUrl,
    MemberStatus status
) {

    public static MemberSignupResult from(Member member) {
        return new MemberSignupResult(
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getNickname(),
            member.getRole(),
            member.getProfileImageUrl(),
            member.getStatus()
        );
    }
}
