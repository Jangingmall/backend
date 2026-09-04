package com.jangingmall.backend.global.config;

import java.util.Set;

public final class PermitAllPaths {

    public static final Set<String> PATHS = Set.of(
        "/api/health",
        "/api/member/signup",
        "/api/member/login",
        "/api/member/token/refresh",
        "/api/member/email-verifications",
        "/api/member/email-verifications/verify",
        "/api/member/oauth2/**",
        "/oauth2/**",
        "/login/oauth2/**"
    );

    private PermitAllPaths() {}
}
