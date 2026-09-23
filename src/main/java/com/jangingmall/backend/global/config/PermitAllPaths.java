package com.jangingmall.backend.global.config;

import java.util.Set;

public final class PermitAllPaths {

    public static final Set<String> PATHS = Set.of(
        "/healthz",
        "/api/chatbot/**",
        "/redoc.html",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/prometheus",
        "/api/member/signup",
        "/api/member/login",
        "/api/member/token/refresh",
        "/api/member/oauth2/kakao",
        "/api/member/oauth2/exchange",
        "/api/member/oauth2/complete-profile",
        "/oauth2/**",
        "/login/oauth2/**"
    );

    private PermitAllPaths() {}
}
