package com.jangingmall.backend.member.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jangingmall.backend.global.common.response.GlobalResponseAdvice;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.MemberAuthenticationService;
import com.jangingmall.backend.member.application.MemberSession;
import com.jangingmall.backend.member.application.MemberSignupResult;
import com.jangingmall.backend.member.application.OAuthIdentity;
import com.jangingmall.backend.member.application.OAuthMemberService;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberStatus;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OAuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, GlobalResponseAdvice.class})
@TestPropertySource(properties = "jwt.refresh-cookie-secure=true")
class OAuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OAuthMemberService oauth;
    @MockitoBean private MemberAuthenticationService authentication;

    @Test
    @DisplayName("최초 소셜 로그인 교환은 임시 가입 토큰을 HttpOnly 쿠키로만 전달한다")
    void exchangesFirstLoginTicket() throws Exception {
        OAuthIdentity identity = new OAuthIdentity("google", "provider-subject", "social@example.com");
        when(oauth.exchange("ticket")).thenReturn(new OAuthMemberService.Grant(null, identity));
        when(oauth.onboarding(identity)).thenReturn("onboarding-token");

        mockMvc.perform(post("/api/member/oauth2/exchange").cookie(new Cookie("oauthTicket", "ticket")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.onboardingRequired").value(true))
            .andExpect(jsonPath("$.data.onboardingToken").doesNotExist())
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.contains("oauthOnboarding=onboarding-token") && value.contains("HttpOnly")));
    }

    @Test
    @DisplayName("추가정보 입력은 임시 쿠키를 지우고 Access Token과 Refresh Cookie를 발급한다")
    void completesProfileFromOnboardingCookie() throws Exception {
        when(oauth.complete(eq("onboarding-token"), any())).thenReturn(
            new MemberSignupResult(7L, "social@example.com", MemberStatus.ACTIVE));
        when(authentication.socialSession(7L)).thenReturn(session());

        mockMvc.perform(post("/api/member/oauth2/complete-profile")
                .cookie(new Cookie("oauthOnboarding", "onboarding-token"))
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name":"김도공","phone":"01012345678","agreements":{
                      "age14OrOlder":true,"termsOfService":true,"privacyCollection":true,"marketing":false}}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.memberId").value(7))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.contains("oauthOnboarding=") && value.contains("Max-Age=0")))
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.contains("refreshToken=refresh-token") && value.contains("HttpOnly")));
    }

    private MemberSession session() {
        return new MemberSession("access-token", "refresh-token", 7L, "social@example.com", "김도공", MemberRole.USER);
    }
}
