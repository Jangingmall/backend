package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.MemberSignupResult;
import com.jangingmall.backend.member.domain.MemberStatus;

public record MemberSignupResponse(Long memberId, String email, MemberStatus status) {

    public static MemberSignupResponse from(MemberSignupResult result) {
        return new MemberSignupResponse(result.memberId(), result.email(), result.status());
    }
}
