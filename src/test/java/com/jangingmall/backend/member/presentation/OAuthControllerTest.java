package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.common.response.GlobalResponseAdvice;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.MemberAuthenticationService;
import com.jangingmall.backend.member.application.MemberSession;
import com.jangingmall.backend.member.application.MemberSignupResult;
import com.jangingmall.backend.member.application.OAuthIdentity;
import com.jangingmall.backend.member.application.OAuthMemberService;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberStatus;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(OAuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, GlobalResponseAdvice.class})
@TestPropertySource(properties = "jwt.refresh-cookie-secure=true")
class OAuthControllerTest extends RestDocsControllerTest {

    @MockitoBean private OAuthMemberService oauth;
    @MockitoBean private MemberAuthenticationService authentication;

    @Test
    @DisplayName("카카오 OAuth 로그인 리다이렉트는 302를 반환한다")
    void kakaoRedirect() throws Exception {
        mockMvc.perform(get("/api/member/oauth2/kakao"))
            .andExpect(status().isFound())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "oauth-kakao-redirect",
                resource(ResourceSnippetParameters.builder()
                    .tag("소셜 로그인")
                    .summary("카카오 로그인 리다이렉트")
                    .description("카카오 OAuth2 인증 페이지로 리다이렉트합니다.")
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("비활성화한 네이버 OAuth 리다이렉트는 존재하지 않는다")
    void rejectNaverRedirect() throws Exception {
        mockMvc.perform(get("/api/member/oauth2/naver"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("최초 소셜 로그인 교환은 onboardingRequired=true와 HttpOnly 온보딩 쿠키를 반환한다")
    void exchangesFirstLoginTicket() throws Exception {
        OAuthIdentity identity = new OAuthIdentity("kakao", "provider-subject", "social@example.com");
        when(oauth.exchange("ticket")).thenReturn(new OAuthMemberService.Grant(null, identity));
        when(oauth.onboarding(identity)).thenReturn("onboarding-token");

        mockMvc.perform(post("/api/member/oauth2/exchange")
                .cookie(new Cookie("oauthTicket", "ticket")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.onboardingRequired").value(true))
            .andExpect(jsonPath("$.data.onboardingToken").doesNotExist())
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(v -> v.contains("oauthOnboarding=onboarding-token") && v.contains("HttpOnly")))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "oauth-exchange",
                resource(ResourceSnippetParameters.builder()
                    .tag("소셜 로그인")
                    .summary("OAuth 티켓 교환")
                    .description("콜백에서 발급된 oauthTicket 쿠키로 Access Token 또는 온보딩 토큰을 교환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.onboardingRequired").type(JsonFieldType.BOOLEAN).description("추가정보 입력 필요 여부"),
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING).optional().description("기존 회원이면 액세스 토큰 발급 (신규 회원은 null)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("추가정보 입력은 온보딩 쿠키를 지우고 Access Token과 Refresh Cookie를 발급한다")
    void completesProfileFromOnboardingCookie() throws Exception {
        when(oauth.complete(eq("onboarding-token"), any())).thenReturn(
            new MemberSignupResult(7L, "social@example.com", "김도공", null, MemberRole.USER, null, MemberStatus.ACTIVE, "kakao"));
        when(authentication.socialSession(7L)).thenReturn(
            new MemberSession("access-token", "refresh-token", 7L, "social@example.com", "김도공", MemberRole.USER, null, null, "kakao"));

        mockMvc.perform(post("/api/member/oauth2/complete-profile")
                .cookie(new Cookie("oauthOnboarding", "onboarding-token"))
                .contentType(APPLICATION_JSON)
                .content(json(new OAuthController.CompleteProfile(
                    "김도공", "01012345678",
                    new MemberSignupRequest.Agreements(true, true, true, null)
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.memberId").value(7))
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(v -> v.contains("oauthOnboarding=") && v.contains("Max-Age=0")))
            .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(v -> v.contains("refreshToken=refresh-token") && v.contains("HttpOnly")))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "oauth-complete-profile",
                resource(ResourceSnippetParameters.builder()
                    .tag("소셜 로그인")
                    .summary("소셜 회원 추가정보 입력")
                    .description("신규 소셜 회원이 이름, 전화번호 등 추가정보를 입력하고 가입을 완료합니다.")
                    .requestFields(
                        fieldWithPath("name").type(JsonFieldType.STRING).description("이름"),
                        fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호 (숫자만)"),
                        fieldWithPath("agreements.age14OrOlder").type(JsonFieldType.BOOLEAN).description("만 14세 이상 동의"),
                        fieldWithPath("agreements.termsOfService").type(JsonFieldType.BOOLEAN).description("서비스 이용약관 동의"),
                        fieldWithPath("agreements.privacyCollection").type(JsonFieldType.BOOLEAN).description("개인정보 수집 동의"),
                        fieldWithPath("agreements.marketing").type(JsonFieldType.BOOLEAN).optional().description("마케팅 수신 동의 (선택)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("data.role").type(JsonFieldType.STRING).description("역할"),
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                        fieldWithPath("data.provider").type(JsonFieldType.STRING).optional().description("소셜 로그인 제공자 (kakao)")
                    ))
                    .build()
                )
            ));
    }
}
