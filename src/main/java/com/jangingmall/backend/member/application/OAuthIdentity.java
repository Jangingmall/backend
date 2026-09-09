package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.util.Locale;

public record OAuthIdentity(String provider,String subject,String email) {
    public OAuthIdentity {
        if (!("kakao".equals(provider) || "google".equals(provider)) || subject == null || subject.isBlank() || subject.length()>255
            || email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length()>255) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        email=email.trim().toLowerCase(Locale.ROOT);
    }
}
