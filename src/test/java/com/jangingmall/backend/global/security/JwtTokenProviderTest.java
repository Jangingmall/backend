package com.jangingmall.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jangingmall.backend.member.domain.MemberRole;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
        new JwtProperties("local-dev-secret-key-minimum-256-bits-for-hs256-algorithm", 1_800_000, 604_800_000)
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
}
