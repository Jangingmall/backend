package com.jangingmall.backend.member.infrastructure;

import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Provider tokens are used only during login and are never retained as application sessions. */
final class TransientOAuthClientRepository implements OAuth2AuthorizedClientRepository {
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId,Authentication principal,HttpServletRequest request) {
        return null;
    }
    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication principal,HttpServletRequest request,HttpServletResponse response) {}
    @Override
    public void removeAuthorizedClient(String registrationId,Authentication principal,HttpServletRequest request,HttpServletResponse response) {}
}
