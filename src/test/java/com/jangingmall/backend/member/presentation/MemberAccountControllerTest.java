package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.MemberAccountService;
import com.jangingmall.backend.member.application.MemberProfile;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.presentation.dto.MemberAccountRequests;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MemberAccountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MemberAccountControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private MemberAccountService accounts;

    @Test
    @DisplayName("내 프로필 수정은 변경된 프로필을 반환한다")
    void updateProfile() throws Exception {
        when(accounts.update(eq(1L), any(), any(), any())).thenReturn(
            new MemberProfile(1L, "artisan@example.com", "새이름", MemberRole.USER, "새닉네임", null, null));

        mockMvc.perform(patch("/api/member/me").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new MemberAccountRequests.Profile("새이름", "새닉네임", null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberId").value(1))
            .andExpect(jsonPath("$.data.name").value("새이름"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-account-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원 계정")
                    .summary("프로필 수정")
                    .description("이름, 닉네임, 전화번호를 선택적으로 수정합니다. null 필드는 변경하지 않습니다.")
                    .requestFields(
                        fieldWithPath("name").type(JsonFieldType.STRING).optional().description("이름 (변경 시)"),
                        fieldWithPath("nickname").type(JsonFieldType.STRING).optional().description("닉네임 (변경 시)"),
                        fieldWithPath("phone").type(JsonFieldType.STRING).optional().description("전화번호 (변경 시, 숫자만)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("회원 ID"),
                        fieldWithPath("data.email").type(JsonFieldType.STRING).description("이메일"),
                        fieldWithPath("data.name").type(JsonFieldType.STRING).description("이름"),
                        fieldWithPath("data.nickname").type(JsonFieldType.STRING).optional().description("닉네임"),
                        fieldWithPath("data.role").type(JsonFieldType.STRING).description("역할"),
                        fieldWithPath("data.profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL"),
                        fieldWithPath("data.provider").type(JsonFieldType.STRING).optional().description("소셜 로그인 제공자")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("비밀번호 변경은 성공 시 200을 반환한다")
    void changePassword() throws Exception {
        mockMvc.perform(patch("/api/member/me/password").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new MemberAccountRequests.Password("current-pw", "new-password1!"))))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-account-change-password",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원 계정")
                    .summary("비밀번호 변경")
                    .description("현재 비밀번호를 확인 후 새 비밀번호로 변경합니다.")
                    .requestFields(
                        fieldWithPath("currentPassword").type(JsonFieldType.STRING).description("현재 비밀번호"),
                        fieldWithPath("newPassword").type(JsonFieldType.STRING).description("새 비밀번호 (8자 이상, 72자 이하)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("응답 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("회원 탈퇴는 성공 시 200을 반환한다")
    void withdraw() throws Exception {
        mockMvc.perform(delete("/api/member/me").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new MemberAccountRequests.Withdrawal("서비스가 불편합니다"))))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-account-withdraw",
                resource(ResourceSnippetParameters.builder()
                    .tag("회원 계정")
                    .summary("회원 탈퇴")
                    .description("탈퇴 사유를 입력하고 회원 탈퇴를 진행합니다.")
                    .requestFields(
                        fieldWithPath("reason").type(JsonFieldType.STRING).description("탈퇴 사유 (최대 500자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).optional().description("응답 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("인증 없이 프로필 수정하면 401을 반환한다")
    @WithAnonymousUser
    void updateProfileWithoutAuth() throws Exception {
        mockMvc.perform(patch("/api/member/me")
                .contentType(APPLICATION_JSON)
                .content(json(new MemberAccountRequests.Profile("이름", null, null))))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("member-account-update-unauthorized", "회원 계정", "프로필 수정 — 인증 없음", "인증 없이 접근하면 401을 반환합니다."));
    }

    private RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
