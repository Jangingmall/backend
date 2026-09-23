package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.Member;

public record MemberProfile(
    Long memberId,
    String email,
    String name,
    MemberRole role,
    String nickname,
    String profileImageUrl,
    String provider,
    String phone
) {
    public MemberProfile(Long memberId, String email, String name, MemberRole role) {
        this(memberId, email, name, role, null, null, null, null);
    }

    public MemberProfile(Long memberId, String email, String name, MemberRole role, String nickname,
                         String profileImageUrl, String provider) {
        this(memberId, email, name, role, nickname, profileImageUrl, provider, null);
    }

    public static MemberProfile from(Member member) {
        return new MemberProfile(member.getId(), member.getEmail(), member.getName(), member.getRole(),
            member.getNickname(), member.getProfileImageUrl(), null, member.getPhone());
    }

    public static MemberProfile from(Member member, String provider) {
        return new MemberProfile(member.getId(), member.getEmail(), member.getName(), member.getRole(),
            member.getNickname(), member.getProfileImageUrl(), provider, member.getPhone());
    }
}
