package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.MemberSession;

public record MemberLoginResponse(
    String accessToken,
    MemberProfileResponse member
) {

    public static MemberLoginResponse from(MemberSession session) {
        return new MemberLoginResponse(
            session.accessToken(),
            new MemberProfileResponse(
                session.memberId(),
                session.email(),
                session.name(),
                session.nickname(),
                session.role(),
                session.profileImageUrl()
            )
        );
    }
}
