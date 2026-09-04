package com.jangingmall.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangingmall.backend.member.domain.MemberRole;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
        new JwtProperties("local-dev-secret-key-minimum-256-bits-for-hs256-algorithm", 1_800_000, 604_800_000)
    );
    private final JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAccessToken() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.ARTISAN);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        jwtAuthenticationFilter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
            authentication.set(SecurityContextHolder.getContext().getAuthentication())
        );

        assertThat(authentication.get().getPrincipal()).isEqualTo(1L);
        assertThat(authentication.get().getAuthorities())
            .extracting("authority")
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ARTISAN");
    }
}
