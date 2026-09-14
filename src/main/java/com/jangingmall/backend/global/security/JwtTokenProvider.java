package com.jangingmall.backend.global.security;

import com.jangingmall.backend.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        return createToken(memberId, role, "ACCESS", properties.accessTokenExpiry());
    }

    public String createRefreshToken(Long memberId, MemberRole role) {
        return createToken(memberId, role, "REFRESH", properties.refreshTokenExpiry());
    }

    public JwtMemberClaims parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!"ACCESS".equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new InvalidTokenTypeException();
        }
        return toMemberClaims(claims);
    }

    public JwtMemberClaims parseRefreshToken(String token) {
        Claims claims = parse(token);
        if (!"REFRESH".equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new InvalidTokenTypeException();
        }
        return toMemberClaims(claims);
    }

    private String createToken(Long memberId, MemberRole role, String tokenType, long expiryMillis) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(memberId.toString())
            .claim(ROLE_CLAIM, role.name())
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expiryMillis)))
            .signWith(signingKey)
            .compact();
    }

    private Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private JwtMemberClaims toMemberClaims(Claims claims) {
        return new JwtMemberClaims(
            Long.valueOf(claims.getSubject()),
            MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class))
        );
    }

    public record JwtMemberClaims(Long memberId, MemberRole role) {
    }

    public static class InvalidTokenTypeException extends RuntimeException {
    }
}
