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
    void acceptsNaverEmail() {
        OAuthIdentity identity = users.identity("naver", Map.of("resultcode", "00", "message", "success",
            "response", Map.of("id", "naver-subject", "email", "naver@example.com")));

        assertThat(identity.provider()).isEqualTo("naver");
        assertThat(identity.subject()).isEqualTo("naver-subject");
    }

    @Test
    void rejectsNaverResponseWithoutEmail() {
        assertThatThrownBy(() -> users.identity("naver", Map.of("response", Map.of("id", "naver-subject"))))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void acceptsVerifiedKakaoEmail() {
        OAuthIdentity identity = users.identity("kakao", Map.of("id", 12345,
            "kakao_account", Map.of("email", "kakao@example.com", "is_email_valid", true, "is_email_verified", true)));

        assertThat(identity.provider()).isEqualTo("kakao");
        assertThat(identity.subject()).isEqualTo("12345");
    }
}
