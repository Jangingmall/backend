package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberRole;

public record MemberSession(
    String accessToken,
    String refreshToken,
    Long memberId,
    String email,
    String name,
    MemberRole role
) {
}
