package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.member.application.MemberSession;
import java.time.Duration;

public record MemberTokenRefreshResponse(
    String accessToken,
    long expiresIn
) {

    public static MemberTokenRefreshResponse from(MemberSession session, JwtProperties properties) {
        return new MemberTokenRefreshResponse(
            session.accessToken(),
            Duration.ofMillis(properties.accessTokenExpiry()).toSeconds()
        );
    }
}
