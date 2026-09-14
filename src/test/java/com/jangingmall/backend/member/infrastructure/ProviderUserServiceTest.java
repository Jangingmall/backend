package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jangingmall.backend.member.application.OAuthIdentity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class ProviderUserServiceTest {

    private final ProviderUserService users = new ProviderUserService(1_000);

    @Test
    void acceptsVerifiedGoogleEmail() {
        OAuthIdentity identity = users.identity("google", Map.of("sub", "google-subject", "email", "google@example.com", "email_verified", true));

        assertThat(identity.provider()).isEqualTo("google");
        assertThat(identity.subject()).isEqualTo("google-subject");
    }

    @Test
    void rejectsGoogleEmailWithoutVerification() {
        assertThatThrownBy(() -> users.identity("google", Map.of("sub", "google-subject", "email", "google@example.com", "email_verified", false)))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void acceptsVerifiedKakaoEmail() {
        OAuthIdentity identity = users.identity("kakao", Map.of("id", 12345,
            "kakao_account", Map.of("email", "kakao@example.com", "is_email_valid", true, "is_email_verified", true)));

        assertThat(identity.provider()).isEqualTo("kakao");
        assertThat(identity.subject()).isEqualTo("12345");
    }
}
