package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.security.JwtProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class MemberCookies {
    private MemberCookies() {}

    public static ResponseCookie refresh(String token,JwtProperties properties) {
        return cookie("refreshToken",token,Duration.ofMillis(properties.refreshTokenExpiry()),"/api/member",properties);
    }

    public static ResponseCookie ticket(String token,Duration ttl,JwtProperties properties) {
        return cookie("oauthTicket",token,ttl,"/api/member/oauth2",properties);
    }

    public static ResponseCookie onboarding(String token,Duration ttl,JwtProperties properties) {
        return cookie("oauthOnboarding",token,ttl,"/api/member/oauth2",properties);
    }

    public static ResponseCookie clearRefresh(JwtProperties properties) {
        return cookie("refreshToken","",Duration.ZERO,"/api/member",properties);
    }

    private static ResponseCookie cookie(String name,String token,Duration ttl,String path,JwtProperties properties) {
        return ResponseCookie.from(name,token).httpOnly(true).secure(properties.refreshCookieSecure())
            .sameSite("Strict").path(path).maxAge(ttl).build();
    }
}
