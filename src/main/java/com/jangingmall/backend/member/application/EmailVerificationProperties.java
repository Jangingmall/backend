package com.jangingmall.backend.member.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "member.email-verification")
public record EmailVerificationProperties(
    @NotNull URI verificationUrl,
    @NotNull URI successRedirectUrl,
    @NotBlank String from,
    @Positive long tokenExpirySeconds
) {
}
