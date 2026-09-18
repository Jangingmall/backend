package com.jangingmall.backend.notification.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.notification.application.NotificationCreateRequest;
import com.jangingmall.backend.notification.application.NotificationResponse;
import com.jangingmall.backend.notification.application.NotificationService;
import com.jangingmall.backend.notification.application.NotificationSseService;
import com.jangingmall.backend.notification.application.UnreadCountResponse;
import com.jangingmall.backend.notification.domain.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import com.epages.restdocs.apispec.SimpleType;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private static final String TAG = "알림";

    private static final NotificationResponse SAMPLE = new NotificationResponse(
        1L, "주문 완료", "청자 상감 다완 주문이 접수되었습니다.",
        NotificationStatus.UNREAD, LocalDateTime.of(2026, 9, 1, 10, 0)
    );

    // ────────────────────────────────── GET /api/notifications ──────────────────────────────────

    @Test
    @DisplayName("목록 조회 — 200 성공")
    @WithMockUser(roles = "USER")
    void findAll_success() throws Exception {
        when(notificationService.findAll(any())).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-list-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("알림 목록 조회")
                    .description("로그인한 회원의 알림 목록을 최신순으로 조회합니다.\n\n"
                        + enumTable("NotificationStatus", entries(
                            "UNREAD", "읽지 않음",
                            "READ", "읽음",
                            "DELETED", "삭제됨"
                        )))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].id").type(JsonFieldType.NUMBER).description("알림 ID"),
                        fieldWithPath("data[].title").type(JsonFieldType.STRING).description("알림 제목"),
                        fieldWithPath("data[].content").type(JsonFieldType.STRING).description("알림 내용"),
                        fieldWithPath("data[].status").type(JsonFieldType.STRING).description("알림 상태 (UNREAD/READ/DELETED)"),
                        fieldWithPath("data[].createdAt").type(JsonFieldType.STRING).description("생성 일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("목록 조회 — 소비자 권한 없으면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void findAll_forbidden_role() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isForbidden());
    }

    // ────────────────────────────── GET /api/notifications/{notificationId} ──────────────────────────────

    @Test
    @DisplayName("단건 조회 — 200 성공")
    @WithMockUser(roles = "USER")
    void findOne_success() throws Exception {
        when(notificationService.findOne(any(), any())).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/notifications/{notificationId}", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-get-one-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("알림 단건 조회")
                    .description("특정 알림의 상세 내용을 조회합니다.")
                    .pathParameters(parameterWithName("notificationId").description("알림 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("알림 ID"),
                        fieldWithPath("data.title").type(JsonFieldType.STRING).description("알림 제목"),
                        fieldWithPath("data.content").type(JsonFieldType.STRING).description("알림 내용"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("알림 상태 (UNREAD/READ/DELETED)"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("생성 일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("단건 조회 — 404 알림 없음")
    @WithMockUser(roles = "USER")
    void findOne_notFound() throws Exception {
        when(notificationService.findOne(any(), any())).thenThrow(new NotFoundException());

        mockMvc.perform(get("/api/notifications/{notificationId}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
            .andDo(documentError(
                "notification-get-one-404", TAG,
                "알림 단건 조회 - 알림 없음",
                "알림 ID에 해당하는 알림이 존재하지 않습니다."
            ));
    }

    @Test
    @DisplayName("단건 조회 — 403 타인 알림 접근")
    @WithMockUser(roles = "USER")
    void findOne_forbidden() throws Exception {
        when(notificationService.findOne(any(), any()))
            .thenThrow(new ForbiddenException("본인의 알림만 접근할 수 있습니다"));

        mockMvc.perform(get("/api/notifications/{notificationId}", 1L))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
            .andDo(documentError(
                "notification-get-one-403", TAG,
                "알림 단건 조회 - 권한 없음",
                "본인의 알림이 아닌 경우 반환됩니다."
            ));
    }

    // ────────────────────────────── PATCH /api/notifications/{notificationId}/read ──────────────────────────────

    @Test
    @DisplayName("읽음 처리 — 200 성공")
    @WithMockUser(roles = "USER")
    void markAsRead_success() throws Exception {
        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-read-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("알림 읽음 처리")
                    .description("알림을 읽음 상태로 변경합니다.")
                    .pathParameters(parameterWithName("notificationId").description("알림 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("읽음 처리 — 404 알림 없음")
    @WithMockUser(roles = "USER")
    void markAsRead_notFound() throws Exception {
        doThrow(new NotFoundException()).when(notificationService).markAsRead(any(), any());

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
            .andDo(documentError(
                "notification-read-404", TAG,
                "알림 읽음 처리 - 알림 없음",
                "알림 ID에 해당하는 알림이 없는 경우입니다."
            ));
    }

    @Test
    @DisplayName("읽음 처리 — 422 삭제된 알림")
    @WithMockUser(roles = "USER")
    void markAsRead_deletedNotification() throws Exception {
        doThrow(new BusinessRuleViolationException("삭제된 알림은 읽음 처리할 수 없습니다"))
            .when(notificationService).markAsRead(any(), any());

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", 1L))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
            .andDo(documentError(
                "notification-read-422", TAG,
                "알림 읽음 처리 - 비즈니스 규칙 위반",
                "삭제된 알림은 읽음 처리할 수 없습니다."
            ));
    }

    // ────────────────────────────── GET /api/notifications/unread-count ──────────────────────────────

    @Test
    @DisplayName("읽지 않은 알림 수 조회 — 200 성공")
    @WithMockUser(roles = "USER")
    void countUnread_success() throws Exception {
        when(notificationService.countUnread(any())).thenReturn(new UnreadCountResponse(3L));

        mockMvc.perform(get("/api/notifications/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount").value(3))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-unread-count-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("읽지 않은 알림 수 조회")
                    .description("로그인한 회원의 읽지 않은 알림 수를 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.unreadCount").type(JsonFieldType.NUMBER).description("읽지 않은 알림 수")
                    ))
                    .build()
                )
            ));
    }

    // ────────────────────────────── PATCH /api/notifications/read-all ──────────────────────────────

    @Test
    @DisplayName("전체 읽음 처리 — 200 성공")
    @WithMockUser(roles = "USER")
    void markAllAsRead_success() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-read-all-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("전체 읽음 처리")
                    .description("로그인한 회원의 읽지 않은 알림을 모두 읽음 처리합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    // ────────────────────────────── DELETE /api/notifications/{notificationId} ──────────────────────────────

    @Test
    @DisplayName("삭제 — 200 성공")
    @WithMockUser(roles = "USER")
    void deleteNotification_success() throws Exception {
        mockMvc.perform(delete("/api/notifications/{notificationId}", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-delete-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("알림 삭제")
                    .description("알림을 삭제 상태로 변경합니다.")
                    .pathParameters(parameterWithName("notificationId").description("알림 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("삭제 — 422 이미 삭제된 알림")
    @WithMockUser(roles = "USER")
    void deleteNotification_alreadyDeleted() throws Exception {
        doThrow(new BusinessRuleViolationException("이미 삭제된 알림입니다"))
            .when(notificationService).delete(any(), any());

        mockMvc.perform(delete("/api/notifications/{notificationId}", 1L))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
            .andDo(documentError(
                "notification-delete-422", TAG,
                "알림 삭제 - 비즈니스 규칙 위반",
                "이미 삭제된 알림을 다시 삭제 요청한 경우입니다."
            ));
    }

    // ────────────────────────────── GET /api/notifications/stream ──────────────────────────────

    @Test
    @DisplayName("SSE 연결 — 200 text/event-stream 반환")
    @WithMockUser(roles = "USER")
    void stream_success() throws Exception {
        when(notificationSseService.subscribe(any())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/notifications/stream")
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-stream-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("SSE 실시간 알림 구독")
                    .description("""
                        SSE(Server-Sent Events)로 실시간 알림을 수신합니다.

                        Browser EventSource는 Authorization 헤더를 설정할 수 없으므로 JWT를 쿼리 파라미터로 전달합니다.

                        연결 직후 `connect` 이벤트가 전송되고, 이후 알림 발생 시 `notification` 이벤트가 전송됩니다.
                        """)
                    .queryParameters(
                        parameterWithName("token").description("JWT 액세스 토큰 (EventSource 사용 시 필수)").optional()
                    )
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("SSE 연결 — 인증 없으면 401")
    @WithAnonymousUser
    void stream_unauthorized() throws Exception {
        mockMvc.perform(get("/api/notifications/stream")
                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(status().isUnauthorized());
    }

    // ────────────────────────────── POST /api/notifications ──────────────────────────────

    @Test
    @DisplayName("알림 생성 — 201 성공")
    @WithMockUser(roles = "USER")
    void create_success() throws Exception {
        when(notificationService.create(any(), any())).thenReturn(SAMPLE);

        NotificationCreateRequest request = new NotificationCreateRequest("주문 완료", "청자 상감 다완 주문이 접수되었습니다.");

        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("주문 완료"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "notification-create-200",
                resource(ResourceSnippetParameters.builder()
                    .tag(TAG)
                    .summary("알림 생성")
                    .description("알림을 생성하고 SSE 구독자에게 실시간으로 전송합니다.\n\n"
                        + enumTable("NotificationStatus", entries(
                            "UNREAD", "읽지 않음",
                            "READ", "읽음",
                            "DELETED", "삭제됨"
                        )))
                    .requestFields(
                        fieldWithPath("title").type(JsonFieldType.STRING).description("알림 제목 (@NotBlank, 최대 255자)"),
                        fieldWithPath("content").type(JsonFieldType.STRING).description("알림 내용 (@NotBlank, 최대 255자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.id").type(JsonFieldType.NUMBER).description("알림 ID"),
                        fieldWithPath("data.title").type(JsonFieldType.STRING).description("알림 제목"),
                        fieldWithPath("data.content").type(JsonFieldType.STRING).description("알림 내용"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("알림 상태 (UNREAD)"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("생성 일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("알림 생성 — 400 title 빈 값")
    @WithMockUser(roles = "USER")
    void create_blankTitle() throws Exception {
        NotificationCreateRequest request = new NotificationCreateRequest("", "알림 내용");

        mockMvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isBadRequest());
    }
}
