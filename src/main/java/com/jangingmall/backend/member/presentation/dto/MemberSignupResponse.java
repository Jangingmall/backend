package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.MemberSignupResult;

public record MemberSignupResponse(
    String accessToken,
    MemberProfileResponse member
) {

    public static MemberSignupResponse from(MemberSignupResult result) {
        return new MemberSignupResponse(
            null,
            new MemberProfileResponse(
                result.memberId(),
                result.email(),
                result.name(),
                result.nickname(),
                result.role(),
                result.profileImageUrl()
            )
        );
    }
}
