package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.Member;

public record MemberProfile(
    Long memberId,
    String email,
    String name,
    MemberRole role,
    String nickname,
    String profileImageUrl
) {
    public MemberProfile(Long memberId, String email, String name, MemberRole role) {
        this(memberId, email, name, role, null, null);
    }

    public static MemberProfile from(Member member) {
        return new MemberProfile(member.getId(), member.getEmail(), member.getName(), member.getRole(),
            member.getNickname(), member.getProfileImageUrl());
    }
}
