package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.MemberProfile;
import com.jangingmall.backend.member.domain.MemberRole;

public record MemberProfileResponse(
    Long memberId,
    String email,
    String name,
    String nickname,
    MemberRole role,
    String profileImageUrl
) {

    public static MemberProfileResponse from(MemberProfile profile) {
        return new MemberProfileResponse(
            profile.memberId(),
            profile.email(),
            profile.name(),
            null,
            profile.role(),
            null
        );
    }
}
