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
    MemberStatus status,
    String provider
) {

    public MemberSignupResult(Long memberId, String email, String name, String nickname,
                              MemberRole role, String profileImageUrl, MemberStatus status) {
        this(memberId, email, name, nickname, role, profileImageUrl, status, null);
    }

    public static MemberSignupResult from(Member member) {
        return from(member, null);
    }

    public static MemberSignupResult from(Member member, String provider) {
        return new MemberSignupResult(
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getNickname(),
            member.getRole(),
            member.getProfileImageUrl(),
            member.getStatus(),
            provider
        );
    }
}
