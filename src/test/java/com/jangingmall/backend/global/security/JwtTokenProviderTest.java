package com.jangingmall.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jangingmall.backend.member.domain.MemberRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
        new JwtProperties(
            "local-dev-secret-key-minimum-256-bits-for-hs256-algorithm",
            1_800_000,
            604_800_000,
            false
        )
    );

    @Test
    void accessTokenContainsMemberAndRole() {
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.ARTISAN);

        JwtTokenProvider.JwtMemberClaims claims = jwtTokenProvider.parseAccessToken(accessToken);

        assertThat(claims.memberId()).isEqualTo(1L);
        assertThat(claims.role()).isEqualTo(MemberRole.ARTISAN);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(1L, MemberRole.USER);

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(refreshToken))
            .isInstanceOf(JwtTokenProvider.InvalidTokenTypeException.class);
    }

    @Test
    void rejectsExpiredAccessToken() {
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(
            new JwtProperties(
                "local-dev-secret-key-minimum-256-bits-for-hs256-algorithm",
                -1_000,
                604_800_000,
                false
            )
        );
        String expiredToken = expiredTokenProvider.createAccessToken(1L, MemberRole.USER);

        assertThatThrownBy(() -> expiredTokenProvider.parseAccessToken(expiredToken))
            .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTamperedAccessToken() {
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);
        char replacement = accessToken.endsWith("a") ? 'b' : 'a';
        String tamperedToken = accessToken.substring(0, accessToken.length() - 1) + replacement;

        assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(tamperedToken))
            .isInstanceOf(JwtException.class);
    }
}
