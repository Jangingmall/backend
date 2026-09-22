package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.MemberAuthenticationService;
import com.jangingmall.backend.member.application.MemberProfile;
import com.jangingmall.backend.member.application.MemberService;
import com.jangingmall.backend.member.application.MemberSession;
import com.jangingmall.backend.member.application.MemberSignupResult;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberStatus;
import com.jangingmall.backend.member.presentation.dto.MemberLoginRequest;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.refresh-cookie-secure=true")
class MemberControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberAuthenticationService memberAuthenticationService;

    @Test
    @DisplayName("정상 회원가입 요청은 201과 회원 정보를 반환한다")
    void signUp() throws Exception {
        when(memberService.signUp(any())).thenReturn(new MemberSignupResult(
            1L, "artisan@example.com", "김도공", null, MemberRole.USER, null, MemberStatus.ACTIVE,
            null, "01012345678"));

        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content(json(new MemberSignupRequest(
                    "artisan@example.com", "password123!", "password123!", "김도공", "01012345678",
                    MemberRole.USER, new MemberSignupRequest.Agreements(true, true, true, true)))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value(nullValue()))
            .andExpect(jsonPath("$.data.member.memberId").value(1))
            .andExpect(jsonPath("$.data.member.phone").value("01012345678"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-signup",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원")
                    .summary("회원가입")
                    .description("이메일 + 비밀번호로 회원가입합니다. 가입 즉시 로그인할 수 있습니다.\n\n"
                        + enumTable("MemberRole", entries("USER", "소비자", "ARTISAN", "장인")))
                    .requestFields(
                        fieldWithPath("email").type(JsonFieldType.STRING).description("이메일 (@NotBlank, @Email, 최대 255자)"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호 (@NotBlank, 8자 이상 72자 이하)"),
                        fieldWithPath("passwordConfirm").type(JsonFieldType.STRING).description("비밀번호 확인 (@NotBlank)"),
                        fieldWithPath("name").type(JsonFieldType.STRING).description("이름 (@NotBlank, 최대 50자)"),
                        fieldWithPath("phone").type(JsonFieldType.STRING).description("전화번호 (@NotBlank, 숫자만 9~20자리)"),
                        fieldWithPath("role").type(JsonFieldType.STRING).description("역할 (@NotNull, MemberRole 값)"),
                        fieldWithPath("agreements.age14OrOlder").type(JsonFieldType.BOOLEAN).description("만 14세 이상 동의 (@NotNull)"),
                        fieldWithPath("agreements.termsOfService").type(JsonFieldType.BOOLEAN).description("서비스 이용약관 동의 (@NotNull)"),
                        fieldWithPath("agreements.privacyCollection").type(JsonFieldType.BOOLEAN).description("개인정보 수집 동의 (@NotNull)"),
                        fieldWithPath("agreements.marketing").type(JsonFieldType.BOOLEAN).optional().description("마케팅 수신 동의 (선택)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING).optional().description("액세스 토큰 (가입 직후는 null)"),
                        fieldWithPath("data.member.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                        fieldWithPath("data.member.email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("data.member.name").type(JsonFieldType.STRING).description("이름"),
                        fieldWithPath("data.member.phone").type(JsonFieldType.STRING).description("전화번호"),
                        fieldWithPath("data.member.nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("data.member.role").type(JsonFieldType.STRING).description("역할"),
                        fieldWithPath("data.member.profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("data.member.provider").type(JsonFieldType.STRING).optional().description("소셜 로그인 제공자 (일반 가입은 null)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("중복 이메일 회원가입은 409 CONFLICT를 반환한다")
    void signUpWithDuplicateEmail() throws Exception {
        when(memberService.signUp(any())).thenThrow(new DomainException(ErrorCode.CONFLICT));

        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content(json(new MemberSignupRequest(
                    "artisan@example.com", "password123!", "password123!", "김도공", "01012345678",
                    MemberRole.USER, new MemberSignupRequest.Agreements(true, true, true, null)))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andDo(documentError("member-signup-conflict", "회원", "회원가입 — 이메일 중복", "이미 가입된 이메일이면 409를 반환합니다."));
    }

    @Test
    @DisplayName("잘못된 입력값 회원가입은 400 INVALID_INPUT을 반환한다")
    void signUpWithInvalidInput() throws Exception {
        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content(json(new MemberSignupRequest(
                    "artisan@example.com", "password123!", "password123!", "김도공", "010-1234-5678",
                    MemberRole.USER, new MemberSignupRequest.Agreements(true, true, true, null)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("정상 로그인은 Access Token과 HttpOnly Refresh Token 쿠키를 반환한다")
    void login() throws Exception {
        when(memberAuthenticationService.login("artisan@example.com", "password")).thenReturn(
            new MemberSession("access-token", "refresh-token", 1L, "artisan@example.com", "김도공", MemberRole.USER,
                null, null, null, "01012345678"));

        mockMvc.perform(post("/api/member/login")
                .contentType(APPLICATION_JSON)
                .content(json(new MemberLoginRequest("artisan@example.com", "password"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(jsonPath("$.data.member.memberId").value(1))
            .andExpect(jsonPath("$.data.member.phone").value("01012345678"))
            .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("refreshToken=refresh-token").contains("HttpOnly").contains("Secure"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-login",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원")
                    .summary("로그인")
                    .description("이메일 + 비밀번호로 로그인합니다. Refresh Token은 HttpOnly 쿠키로 발급됩니다.")
                    .requestFields(
                        fieldWithPath("email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("password").type(JsonFieldType.STRING).description("비밀번호")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("액세스 토큰"),
                        fieldWithPath("data.member.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                        fieldWithPath("data.member.email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("data.member.name").type(JsonFieldType.STRING).description("이름"),
                        fieldWithPath("data.member.phone").type(JsonFieldType.STRING).description("전화번호"),
                        fieldWithPath("data.member.nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("data.member.role").type(JsonFieldType.STRING).description("역할"),
                        fieldWithPath("data.member.profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("data.member.provider").type(JsonFieldType.STRING).optional().description("소셜 로그인 제공자 (일반 가입은 null)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("잘못된 인증정보 로그인은 401 UNAUTHORIZED를 반환한다")
    void loginWithInvalidCredentials() throws Exception {
        when(memberAuthenticationService.login(any(), any())).thenThrow(new DomainException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(post("/api/member/login")
                .contentType(APPLICATION_JSON)
                .content(json(new MemberLoginRequest("artisan@example.com", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
            .andDo(documentError("member-login-unauthorized", "회원", "로그인 — 인증 실패", "이메일 또는 비밀번호가 틀리면 401을 반환합니다."));
    }

    @Test
    @DisplayName("Refresh Token 쿠키로 새 Access Token을 반환한다")
    void refresh() throws Exception {
        when(memberAuthenticationService.refresh("refresh-token")).thenReturn(
            new MemberSession("new-access-token", "new-refresh-token", 1L, "artisan@example.com", "김도공", MemberRole.USER));

        mockMvc.perform(post("/api/member/token/refresh")
                .cookie(new Cookie("refreshToken", "refresh-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.data.expiresIn").value(1800))
            .andExpect(jsonPath("$.data.member").doesNotExist())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-token-refresh",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원")
                    .summary("토큰 갱신")
                    .description("HttpOnly 쿠키의 Refresh Token으로 새 Access Token을 발급합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.accessToken").type(JsonFieldType.STRING).description("새 액세스 토큰"),
                        fieldWithPath("data.expiresIn").type(JsonFieldType.NUMBER).description("만료 시간 (초)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("로그아웃은 Refresh Token 쿠키를 만료시킨다")
    void logout() throws Exception {
        mockMvc.perform(post("/api/member/logout")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("refreshToken=").contains("Max-Age=0"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-logout",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원")
                    .summary("로그아웃")
                    .description("Refresh Token 쿠키를 만료시킵니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("응답 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("인증된 사용자는 내 정보를 조회한다")
    void getMe() throws Exception {
        when(memberAuthenticationService.getProfile(1L)).thenReturn(
            new MemberProfile(1L, "artisan@example.com", "김도공", MemberRole.USER, null, null, "kakao", "01012345678"));

        mockMvc.perform(get("/api/member/me")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberId").value(1))
            .andExpect(jsonPath("$.data.phone").value("01012345678"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-get-me",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원")
                    .summary("내 정보 조회")
                    .description("액세스 토큰으로 인증된 회원의 프로필 정보를 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("data.name").type(JsonFieldType.STRING).description("이름"),
                        fieldWithPath("data.phone").type(JsonFieldType.STRING).description("전화번호"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("data.role").type(JsonFieldType.STRING).description("역할"),
                        fieldWithPath("data.profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("data.provider").type(JsonFieldType.STRING).optional().description("소셜 로그인 제공자 (kakao, 일반 가입은 null)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("Access Token 없이 내 정보를 조회하면 401 UNAUTHORIZED를 반환한다")
    @WithAnonymousUser
    void getMeWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/member/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
            .andDo(documentError("member-get-me-unauthorized", "회원", "내 정보 조회 — 인증 없음", "인증 없이 접근하면 401을 반환합니다."));
    }
}
