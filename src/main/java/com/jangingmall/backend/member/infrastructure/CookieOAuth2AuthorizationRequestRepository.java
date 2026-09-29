package com.jangingmall.backend.member.infrastructure;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.io.*;
import java.util.Base64;

/**
 * Stores the OAuth2 authorization request in an HttpOnly cookie so that
 * STATELESS sessions can still verify the `state` parameter on callback.
 */
final class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_MAX_AGE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response);
            return;
        }
        Cookie cookie = new Cookie(COOKIE_NAME, serialize(authorizationRequest));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setSecure(true);
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest stored = readCookie(request);
        if (stored != null) {
            deleteCookie(request, response);
        }
        return stored;
    }

    private OAuth2AuthorizationRequest readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return deserialize(cookie.getValue());
            }
        }
        return null;
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() == null) return;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                Cookie blank = new Cookie(COOKIE_NAME, "");
                blank.setPath("/");
                blank.setMaxAge(0);
                blank.setHttpOnly(true);
                blank.setSecure(true);
                response.addCookie(blank);
                return;
            }
        }
    }

    private static String serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(request);
            return Base64.getUrlEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    private static OAuth2AuthorizationRequest deserialize(String value) {
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(Base64.getUrlDecoder().decode(value)))) {
            return (OAuth2AuthorizationRequest) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}
