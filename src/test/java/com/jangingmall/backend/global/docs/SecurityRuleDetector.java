package com.jangingmall.backend.global.docs;

import com.jangingmall.backend.global.config.PermitAllPaths;

import java.util.Set;

/**
 * SecurityConfig 규칙을 읽어 엔드포인트별 Security 에러코드를 결정한다.
 *
 * <p>현재 SecurityConfig: anyRequest().authenticated(), role 규칙 없음.
 * - permitAll 경로 → Security 에러코드 없음
 * - 그 외 → UNAUTHORIZED (JWT 없음·만료 시 Security 필터가 401 반환)
 * - FORBIDDEN: @PreAuthorize/hasRole 없으므로 자동 추가하지 않는다.
 *   ForbiddenException은 도메인 BusinessException이므로 ASM으로 탐지된다.
 *
 * <p>FORBIDDEN 자동 탐지가 필요한 경우: @PreAuthorize/hasRole 도입 시 이 클래스를 확장한다.
 */
class SecurityRuleDetector {

    private static final String UNAUTHORIZED = "UNAUTHORIZED";

    /**
     * 경로(path)가 인증이 필요한 엔드포인트인지 판단해 필요한 Security 에러코드를 반환한다.
     *
     * @param httpMethodAndPath "GET /api/notifications/{notificationId}" 형식
     */
    Set<String> detect(String httpMethodAndPath) {
        String path = extractPath(httpMethodAndPath);
        if (isPermitAll(path)) {
            return Set.of();
        }
        return Set.of(UNAUTHORIZED);
    }

    private boolean isPermitAll(String path) {
        for (String permitted : PermitAllPaths.PATHS) {
            if (path.equals(permitted) || path.startsWith(permitted + "/")) {
                return true;
            }
        }
        return false;
    }

    private String extractPath(String httpMethodAndPath) {
        int spaceIndex = httpMethodAndPath.indexOf(' ');
        if (spaceIndex < 0) return httpMethodAndPath;
        return httpMethodAndPath.substring(spaceIndex + 1);
    }
}
