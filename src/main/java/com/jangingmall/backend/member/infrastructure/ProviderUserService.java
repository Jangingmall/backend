package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.OAuthIdentity;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class ProviderUserService implements OAuth2UserService<OAuth2UserRequest,OAuth2User> {
    private static final Logger log = LoggerFactory.getLogger(ProviderUserService.class);
    private final RestClient client;

    public ProviderUserService(@Value("${member.oauth.http-timeout-millis:5000}") long timeoutMillis) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
        client=RestClient.builder().requestFactory(factory).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2User loadUser(OAuth2UserRequest request) {
        try {
            Map<String,Object> attributes=client.get().uri(request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri())
                .headers(headers->headers.setBearerAuth(request.getAccessToken().getTokenValue())).retrieve().body(Map.class);
            OAuthIdentity identity=identity(request.getClientRegistration().getRegistrationId(),Objects.requireNonNull(attributes));
            return new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("provider",identity.provider(),"subject",identity.subject(),"email",identity.email()),"subject");
        } catch (OAuth2AuthenticationException exception) {
            log.warn("OAuth2 user info mapping failed provider={} error={}", request.getClientRegistration().getRegistrationId(), exception.getError().getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("OAuth2 user info fetch failed provider={} reason={}", request.getClientRegistration().getRegistrationId(), exception.getMessage(), exception);
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
        }
    }

    public OAuthIdentity identity(String provider,Map<String,Object> attributes) {
        if ("kakao".equals(provider)) {
            var kakao=(Map<String,Object>)attributes.getOrDefault("kakao_account",Map.of());
            String id=Objects.toString(attributes.get("id"),"");
            String email=Objects.toString(kakao.get("email"),"");
            if (id.isBlank() || email.isBlank()) {
                throw new OAuth2AuthenticationException(new OAuth2Error("missing_email"));
            }
            return new OAuthIdentity(provider,id,email);
        }
        if ("naver".equals(provider)) {
            var response=(Map<String,Object>)attributes.getOrDefault("response",Map.of());
            String id=Objects.toString(response.get("id"),"");
            String email=Objects.toString(response.get("email"),"");
            if (id.isBlank() || email.isBlank()) {
                throw new OAuth2AuthenticationException(new OAuth2Error("unverified_email"));
            }
            return new OAuthIdentity(provider,id,email);
        }
        throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
    }
}
