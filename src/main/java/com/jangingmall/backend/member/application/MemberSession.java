package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberRole;

public record MemberSession(
    String accessToken,
    String refreshToken,
    Long memberId,
    String email,
    String name,
    MemberRole role,
    String nickname,
    String profileImageUrl,
    String provider,
    String phone
) {
    public MemberSession(String accessToken, String refreshToken, Long memberId, String email, String name, MemberRole role) {
        this(accessToken, refreshToken, memberId, email, name, role, null, null, null, null);
    }

    public MemberSession(String accessToken, String refreshToken, Long memberId, String email, String name,
                         MemberRole role, String nickname, String profileImageUrl, String provider) {
        this(accessToken, refreshToken, memberId, email, name, role, nickname, profileImageUrl, provider, null);
    }
}
