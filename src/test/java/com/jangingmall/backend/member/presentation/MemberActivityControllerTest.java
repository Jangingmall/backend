package com.jangingmall.backend.member.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.CursorPage;
import com.jangingmall.backend.member.application.MemberActivityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import com.epages.restdocs.apispec.SimpleType;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberActivityController.class)
@Import(SecurityConfig.class)
class MemberActivityControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private MemberActivityService activities;

    private static final Map<String, Object> SUBSCRIPTION_ITEM = Map.of(
        "artisanId", 10L,
        "businessName", "김도공 도예",
        "notificationsEnabled", true,
        "newProductCount", 2L
    );

    @Test
    @DisplayName("장인 구독 — 정상 요청은 200을 반환한다")
    @WithMockUser(roles = "USER")
    void subscribe() throws Exception {
        mockMvc.perform(post("/api/member/artisans/{artisanId}/subscribe", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-subscribe",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인 구독")
                    .summary("장인 구독")
                    .description("장인을 구독합니다. 이미 구독 중이면 무시합니다.")
                    .pathParameters(parameterWithName("artisanId").description("장인 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("반환값 없음")
                    ))
                    .build()
                )
            ));

        verify(activities).subscribe(any(), eq(10L));
    }

    @Test
    @DisplayName("장인 구독 — 존재하지 않는 장인이면 404를 반환한다")
    @WithMockUser(roles = "USER")
    void subscribeNotFound() throws Exception {
        doThrow(new DomainException(ErrorCode.NOT_FOUND))
            .when(activities).subscribe(any(), eq(99L));

        mockMvc.perform(post("/api/member/artisans/{artisanId}/subscribe", 99L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
            .andDo(documentError("member-subscribe-not-found", "장인 구독", "장인 구독 — 장인 없음",
                "존재하지 않는 장인을 구독하면 404를 반환합니다."));
    }

    @Test
    @DisplayName("장인 구독 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void subscribeUnauthorized() throws Exception {
        mockMvc.perform(post("/api/member/artisans/{artisanId}/subscribe", 10L))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("member-subscribe-unauthorized", "장인 구독", "장인 구독 — 인증 없음",
                "인증 없이 구독하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("장인 구독 취소 — 정상 요청은 200을 반환한다")
    @WithMockUser(roles = "USER")
    void unsubscribe() throws Exception {
        mockMvc.perform(delete("/api/member/artisans/{artisanId}/subscribe", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-unsubscribe",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인 구독")
                    .summary("장인 구독 취소")
                    .description("장인 구독을 취소합니다. 구독하지 않은 경우에도 200을 반환합니다.")
                    .pathParameters(parameterWithName("artisanId").description("장인 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("반환값 없음")
                    ))
                    .build()
                )
            ));

        verify(activities).unsubscribe(any(), eq(10L));
    }

    @Test
    @DisplayName("장인 구독 취소 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void unsubscribeUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/member/artisans/{artisanId}/subscribe", 10L))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("member-unsubscribe-unauthorized", "장인 구독", "장인 구독 취소 — 인증 없음",
                "인증 없이 구독 취소하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("구독 목록 조회 — 정상 요청은 커서 페이지를 반환한다")
    @WithMockUser(roles = "USER")
    void subscriptions() throws Exception {
        CursorPage<Map<String, Object>> page = new CursorPage<>(List.of(SUBSCRIPTION_ITEM), null, false, 1L);
        when(activities.subscriptions(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/artisans/subscriptions")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-subscriptions",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인 구독")
                    .summary("구독 장인 목록")
                    .description("내가 구독한 장인 목록을 커서 페이지네이션으로 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("구독 장인 목록"),
                        fieldWithPath("data.items[].artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.items[].businessName").type(JsonFieldType.STRING).description("장인 상호명"),
                        fieldWithPath("data.items[].notificationsEnabled").type(JsonFieldType.BOOLEAN).description("알림 수신 여부"),
                        fieldWithPath("data.items[].newProductCount").type(JsonFieldType.NUMBER).description("신규 상품 수"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 구독 수")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("구독 목록 조회 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void subscriptionsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/member/artisans/subscriptions"))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("member-subscriptions-unauthorized", "장인 구독", "구독 목록 — 인증 없음",
                "인증 없이 구독 목록을 조회하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("알림 설정 변경 — 정상 요청은 200을 반환한다")
    @WithMockUser(roles = "USER")
    void notifications() throws Exception {
        mockMvc.perform(patch("/api/member/artisans/subscriptions/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new MemberActivityController.Notifications(true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-notifications",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인 구독")
                    .summary("구독 알림 설정")
                    .description("구독한 모든 장인에 대한 알림 수신 여부를 일괄 설정합니다.")
                    .requestFields(
                        fieldWithPath("notificationsEnabled").type(JsonFieldType.BOOLEAN).description("알림 수신 여부")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("반환값 없음")
                    ))
                    .build()
                )
            ));

        verify(activities).notifications(any(), eq(true));
    }

    @Test
    @DisplayName("알림 설정 변경 — notificationsEnabled가 null이면 400을 반환한다")
    @WithMockUser(roles = "USER")
    void notificationsInvalidInput() throws Exception {
        mockMvc.perform(patch("/api/member/artisans/subscriptions/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new MemberActivityController.Notifications(null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
            .andDo(documentError("member-notifications-invalid", "장인 구독", "알림 설정 — 잘못된 입력",
                "notificationsEnabled가 null이면 400을 반환합니다."));
    }

    @Test
    @DisplayName("알림 설정 변경 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void notificationsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/member/artisans/subscriptions/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new MemberActivityController.Notifications(true))))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("member-notifications-unauthorized", "장인 구독", "알림 설정 — 인증 없음",
                "인증 없이 알림 설정을 변경하면 401을 반환합니다."));
    }
}
