package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberStatus;

public record MemberSignupResult(Long memberId, String email, MemberStatus status) {
}
