package com.jangingmall.backend.member.infrastructure;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.client.RestClient;

@Configuration
public class MemberOAuthSecurity {
    private final ProviderUserService users;
    private final OAuthLoginHandlers handlers;
    private final long timeout;

    public MemberOAuthSecurity(ProviderUserService users,OAuthLoginHandlers handlers,
                               @Value("${member.oauth.http-timeout-millis:5000}") long timeout) {
        this.users=users;
        this.handlers=handlers;
        this.timeout=timeout;
    }

    public void configure(HttpSecurity http) throws Exception {
        http.securityContext(context->context.securityContextRepository(new NullSecurityContextRepository()))
            .oauth2Login(oauth->oauth.userInfoEndpoint(info->info.userService(users))
                .tokenEndpoint(token->token.accessTokenResponseClient(tokenClient()))
                .authorizedClientRepository(new TransientOAuthClientRepository())
                .successHandler(handlers).failureHandler(handlers));
    }

    private RestClientAuthorizationCodeTokenResponseClient tokenClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build());
        factory.setReadTimeout(Duration.ofMillis(timeout));
        var client=RestClient.builder().requestFactory(factory)
            .configureMessageConverters(converters->converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build();
        var tokenClient=new RestClientAuthorizationCodeTokenResponseClient();
        tokenClient.setRestClient(client);
        return tokenClient;
    }
}
