package com.jangingmall.backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long accessTokenExpiry,
    long refreshTokenExpiry,
    boolean refreshCookieSecure
) {
}
