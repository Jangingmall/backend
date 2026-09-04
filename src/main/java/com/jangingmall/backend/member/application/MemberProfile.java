package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberRole;

public record MemberProfile(
    Long memberId,
    String email,
    String name,
    MemberRole role
) {
}
