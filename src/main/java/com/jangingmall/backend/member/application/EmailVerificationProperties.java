package com.jangingmall.backend.member.application;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "member.email-verification")
public record EmailVerificationProperties(
    URI verificationUrl,
    long tokenExpirySeconds
) {
    public EmailVerificationProperties {
        if (verificationUrl == null) {
            verificationUrl = URI.create("http://localhost:3000/email/verify");
        }
        if (tokenExpirySeconds <= 0) {
            tokenExpirySeconds = 1800;
        }
    }
}
