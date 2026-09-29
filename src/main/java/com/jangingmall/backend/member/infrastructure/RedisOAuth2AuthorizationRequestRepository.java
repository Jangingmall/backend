package com.jangingmall.backend.member.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

@Slf4j
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
        if (state == null) {
            log.debug("OAuth2 load: state param missing, uri={}", request.getRequestURI());
            return null;
        }
        OAuth2AuthorizationRequest found = redisTemplate.opsForValue().get(KEY_PREFIX + state);
        log.debug("OAuth2 load: state={} found={}", state, found != null);
        return found;
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
        log.debug("OAuth2 save: state={}", authorizationRequest.getState());
        redisTemplate.opsForValue().set(KEY_PREFIX + authorizationRequest.getState(),
            authorizationRequest, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
        HttpServletResponse response) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            log.warn("OAuth2 remove: state param missing, uri={}", request.getRequestURI());
            return null;
        }
        String key = KEY_PREFIX + state;
        OAuth2AuthorizationRequest authorizationRequest = redisTemplate.opsForValue().get(key);
        log.debug("OAuth2 remove: state={} found={}", state, authorizationRequest != null);
        if (authorizationRequest != null) {
            redisTemplate.delete(key);
        }
        return authorizationRequest;
    }
}
