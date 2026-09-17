package com.jangingmall.backend.global.security;

import com.jangingmall.backend.global.config.InternalApiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallbackFilterTest {

    private static final String CONFIGURED_TOKEN = "test-backend-auth-token";

    private AiCallbackFilter filterWith(String token) {
        return new AiCallbackFilter(new InternalApiProperties(token));
    }

    // ── 성공 경로 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("올바른 Bearer 토큰이면 /internal/** 요청을 통과시키고 ROLE_AGENT를 SecurityContext에 설정한다")
    void passesWithValidToken() throws Exception {
        SecurityContextHolder.clearContext();
        AiCallbackFilter filter = filterWith(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/generations/1/complete");
        request.addHeader("Authorization", "Bearer " + CONFIGURED_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
            .anyMatch(a -> AiCallbackFilter.ROLE_AGENT.equals(a.getAuthority()));
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("/internal/** 이 아닌 경로는 AGENT 토큰이 있어도 필터를 skip하고 SecurityContext를 설정하지 않는다")
    void skipsNonInternalPathsAndDoesNotSetAgentAuth() throws Exception {
        SecurityContextHolder.clearContext();
        AiCallbackFilter filter = filterWith(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/1");
        request.addHeader("Authorization", "Bearer " + CONFIGURED_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        SecurityContextHolder.clearContext();
    }

    // ── 인증 실패 경우의 수 ──────────────────────────────────────────────────

    @Test
    @DisplayName("env 미설정(빈 문자열) — /internal/** 호출 시 무조건 401을 반환한다")
    void rejects401WhenTokenNotConfigured() throws Exception {
        AiCallbackFilter filter = filterWith("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/generations/1/complete");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void rejects401WhenHeaderMissing() throws Exception {
        AiCallbackFilter filter = filterWith(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/generations/1/complete");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("토큰 값이 틀리면 401을 반환한다")
    void rejects401WhenTokenMismatch() throws Exception {
        AiCallbackFilter filter = filterWith(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/generations/1/complete");
        request.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두사 없는 토큰은 401을 반환한다")
    void rejects401WhenNoBearerPrefix() throws Exception {
        AiCallbackFilter filter = filterWith(CONFIGURED_TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/generations/1/complete");
        request.addHeader("Authorization", CONFIGURED_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
