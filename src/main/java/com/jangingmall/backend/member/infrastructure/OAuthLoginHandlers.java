package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.member.application.*;
import com.jangingmall.backend.member.presentation.MemberCookies;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.*;
import org.springframework.stereotype.Component;

@Component
public class OAuthLoginHandlers implements AuthenticationSuccessHandler,AuthenticationFailureHandler {
    private static final Logger log=LoggerFactory.getLogger(OAuthLoginHandlers.class);
    private final OAuthMemberService oauth;
    private final JwtProperties jwt;
    private final String redirectUrl;

    public OAuthLoginHandlers(OAuthMemberService oauth,JwtProperties jwt,
                              @Value("${member.oauth.frontend-redirect-url:http://localhost:3000/oauth/callback}") String redirectUrl) {
        this.oauth=oauth;
        this.jwt=jwt;
        this.redirectUrl=redirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException {
        try {
            var principal=((OAuth2AuthenticationToken)authentication).getPrincipal();
            String ticket=oauth.createTicket(new OAuthIdentity(principal.getAttribute("provider"),principal.getAttribute("subject"),principal.getAttribute("email")));
            response.addHeader(HttpHeaders.SET_COOKIE,MemberCookies.ticket(ticket,Duration.ofMinutes(1),jwt).toString());
            finish(request,response,redirectUrl);
        } catch (RuntimeException exception) {
            log.warn("OAuth account validation failed: {}",exception.getClass().getSimpleName());
            finish(request,response,redirectUrl+"?error=OAUTH_LOGIN_FAILED");
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,HttpServletResponse response,AuthenticationException exception) throws IOException {
        log.warn("OAuth provider authentication failed: {}",exception.getClass().getSimpleName());
        finish(request,response,redirectUrl+"?error=OAUTH_LOGIN_FAILED");
    }

    private void finish(HttpServletRequest request,HttpServletResponse response,String location) throws IOException {
        java.util.Optional.ofNullable(request.getSession(false)).ifPresent(HttpSession::invalidate);
        SecurityContextHolder.clearContext();
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
        response.setHeader("Referrer-Policy","no-referrer");
        response.sendRedirect(location);
    }
}
