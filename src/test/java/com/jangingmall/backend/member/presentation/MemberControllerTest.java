package com.jangingmall.backend.member.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.MemberAuthenticationService;
import com.jangingmall.backend.member.application.MemberProfile;
import com.jangingmall.backend.member.application.MemberService;
import com.jangingmall.backend.member.application.MemberSession;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.application.MemberSignupResult;
import com.jangingmall.backend.member.domain.MemberStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberAuthenticationService memberAuthenticationService;

    @Test
    @DisplayName("정상 회원가입 요청은 201과 이메일 인증 대기 상태를 반환한다")
    void signUp() throws Exception {
        when(memberService.signUp(any())).thenReturn(
            new MemberSignupResult(1L, "artisan@example.com", MemberStatus.PENDING_VERIFICATION)
        );

        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value(201))
            .andExpect(jsonPath("$.data.memberId").value(1))
            .andExpect(jsonPath("$.data.email").value("artisan@example.com"))
            .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .doesNotContain("\\\"errorCode\\\""));
    }

    @Test
    @DisplayName("형식이 올바르지 않은 요청값은 INVALID_INPUT을 반환한다")
    void signUpWithInvalidInput() throws Exception {
        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content(validRequest().replace("01012345678", "010-1234-5678")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("중복 이메일은 CONFLICT를 반환한다")
    void signUpWithDuplicateEmail() throws Exception {
        when(memberService.signUp(any())).thenThrow(new DomainException(ErrorCode.CONFLICT));

        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @DisplayName("Request Body가 없으면 REQUEST_INVALID을 반환한다")
    void signUpWithoutRequestBody() throws Exception {
        mockMvc.perform(post("/api/member/signup").contentType(APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"));
    }

    @Test
    @DisplayName("잘못된 JSON이면 REQUEST_BODY_MALFORMED를 반환한다")
    void signUpWithMalformedRequestBody() throws Exception {
        mockMvc.perform(post("/api/member/signup")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_MALFORMED"));
    }

    @Test
    @DisplayName("정상 로그인은 Access Token과 HttpOnly Refresh Token 쿠키를 반환한다")
    void login() throws Exception {
        when(memberAuthenticationService.login("artisan@example.com", "password")).thenReturn(
            new MemberSession(
                "access-token",
                "refresh-token",
                1L,
                "artisan@example.com",
                "김도공",
                MemberRole.USER
            )
        );

        mockMvc.perform(post("/api/member/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"email":"artisan@example.com","password":"password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").value("access-token"))
            .andExpect(jsonPath("$.data.member.memberId").value(1))
            .andExpect(result -> assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("refreshToken=refresh-token")
                .contains("HttpOnly"));
    }

    @Test
    @DisplayName("인증된 사용자는 내 정보를 조회한다")
    void getMe() throws Exception {
        when(memberAuthenticationService.getProfile(1L)).thenReturn(
            new MemberProfile(1L, "artisan@example.com", "김도공", MemberRole.USER)
        );
        mockMvc.perform(get("/api/member/me")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                    1L,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberId").value(1))
            .andExpect(jsonPath("$.data.email").value("artisan@example.com"))
            .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("Access Token 없이 내 정보를 조회하면 UNAUTHORIZED를 반환한다")
    void getMeWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/member/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    private String validRequest() {
        return """
            {
              "email": "artisan@example.com",
              "password": "password",
              "passwordConfirm": "password",
              "name": "김도공",
              "phone": "01012345678",
              "role": "USER",
              "agreements": {
                "age14OrOlder": true,
                "termsOfService": true,
                "privacyCollection": true,
                "marketing": true
              }
            }
            """;
    }
}
