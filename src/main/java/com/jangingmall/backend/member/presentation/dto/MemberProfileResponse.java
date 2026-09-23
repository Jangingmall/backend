package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.MemberProfile;
import com.jangingmall.backend.member.domain.MemberRole;

public record MemberProfileResponse(
    Long memberId,
    String email,
    String name,
    String nickname,
    MemberRole role,
    String profileImageUrl,
    String provider,
    String phone
) {

    public MemberProfileResponse(Long memberId, String email, String name, String nickname,
                                 MemberRole role, String profileImageUrl) {
        this(memberId, email, name, nickname, role, profileImageUrl, null, null);
    }

    public MemberProfileResponse(Long memberId, String email, String name, String nickname,
                                 MemberRole role, String profileImageUrl, String provider) {
        this(memberId, email, name, nickname, role, profileImageUrl, provider, null);
    }

    public static MemberProfileResponse from(MemberProfile profile) {
        return new MemberProfileResponse(
            profile.memberId(),
            profile.email(),
            profile.name(),
            profile.nickname(),
            profile.role(),
            profile.profileImageUrl(),
            profile.provider(),
            profile.phone()
        );
    }
}
