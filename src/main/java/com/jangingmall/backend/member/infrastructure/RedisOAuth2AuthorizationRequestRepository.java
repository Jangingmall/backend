package com.jangingmall.backend.member.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

@Component
public class RedisOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String KEY_PREFIX = "oauth2:auth-request:";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final RedisTemplate<String, OAuth2AuthorizationRequest> redisTemplate;

    public RedisOAuth2AuthorizationRequestRepository(
        RedisTemplate<String, OAuth2AuthorizationRequest> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) return null;
        return redisTemplate.opsForValue().get(KEY_PREFIX + state);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
        HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            String state = request.getParameter(OAuth2ParameterNames.STATE);
            if (state != null) {
                redisTemplate.delete(KEY_PREFIX + state);
            }
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + authorizationRequest.getState(),
            authorizationRequest, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
        HttpServletResponse response) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) return null;
        String key = KEY_PREFIX + state;
        OAuth2AuthorizationRequest authorizationRequest = redisTemplate.opsForValue().get(key);
        if (authorizationRequest != null) {
            redisTemplate.delete(key);
        }
        return authorizationRequest;
    }
}
