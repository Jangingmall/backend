package com.jangingmall.backend.global.security;

import com.jangingmall.backend.global.config.InternalApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class AiCallbackFilter extends OncePerRequestFilter {

    static final String ROLE_AGENT = "ROLE_AGENT";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    // AGENT 토큰으로 접근을 허용하는 공개 API 경로 (정확히 일치)
    static final String PRESIGNED_URL_PATH = "/api/images/presigned-url";

    private final InternalApiProperties internalApiProperties;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            handleInternalPath(request, response, filterChain);
            return;
        }

        if (PRESIGNED_URL_PATH.equals(path)) {
            tryAgentAuth(request);
        }

        filterChain.doFilter(request, response);
    }

    // /internal/** — fail-closed: 토큰 없거나 불일치 시 즉시 401
    private void handleInternalPath(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws IOException, ServletException {
        String configuredToken = internalApiProperties.backendAuthToken();
        if (!StringUtils.hasText(configuredToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!configuredToken.equals(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("ai-agent", null, List.of(new SimpleGrantedAuthority(ROLE_AGENT)))
        );
        filterChain.doFilter(request, response);
    }

    // allowlist 경로 — additive: 유효 토큰이면 AGENT 설정, 아니면 통과 (JWT filter가 처리)
    private void tryAgentAuth(HttpServletRequest request) {
        String configuredToken = internalApiProperties.backendAuthToken();
        if (!StringUtils.hasText(configuredToken)) {
            return;
        }
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!configuredToken.equals(token)) {
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("ai-agent", null, List.of(new SimpleGrantedAuthority(ROLE_AGENT)))
        );
    }
}
