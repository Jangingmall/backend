package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.OAuthIdentity;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
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
        } catch (RuntimeException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
        }
    }

    @SuppressWarnings("unchecked")
    public OAuthIdentity identity(String provider,Map<String,Object> attributes) {
        if ("google".equals(provider) && Boolean.TRUE.equals(attributes.get("email_verified"))) {
            return new OAuthIdentity(provider,Objects.toString(attributes.get("sub"),""),Objects.toString(attributes.get("email"),""));
        }
        var kakao=(Map<String,Object>)attributes.getOrDefault("kakao_account",Map.of());
        if ("kakao".equals(provider) && Boolean.TRUE.equals(kakao.get("is_email_verified")) && Boolean.TRUE.equals(kakao.get("is_email_valid"))) {
            return new OAuthIdentity(provider,Objects.toString(attributes.get("id"),""),Objects.toString(kakao.get("email"),""));
        }
        throw new OAuth2AuthenticationException(new OAuth2Error("unverified_email"));
    }
}
