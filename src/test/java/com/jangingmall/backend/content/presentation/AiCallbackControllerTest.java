package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.jangingmall.backend.content.application.BeToAiPersistAckResponse;
import com.jangingmall.backend.content.application.GenerationResponse;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiCallbackController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal-api.backend-auth-token=test-agent-token")
class AiCallbackControllerTest extends RestDocsControllerTest {

    private static final String AGENT_TOKEN = "test-agent-token";
    private static final String BEARER_AGENT = "Bearer " + AGENT_TOKEN;

    @MockitoBean
    private GenerationService generationService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 10, 0, 0);

    private static final GenerationResponse COMPLETED_RESPONSE = new GenerationResponse(
        1L, 10L, GenerationStatus.COMPLETED, NOW, NOW.plusMinutes(1)
    );

    private static final org.springframework.restdocs.payload.FieldDescriptor[] GENERATION_FIELDS = {
        fieldWithPath("data.generationId").type(JsonFieldType.NUMBER).description("생성 요청 ID"),
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("생성 상태 (COMPLETED | FAILED)"),
        fieldWithPath("data.requestedAt").type(JsonFieldType.STRING).description("요청 시각"),
        fieldWithPath("data.completedAt").type(JsonFieldType.STRING).optional().description("완료 시각"),
    };

    // ── JSON 콜백 — 성공 경로 ────────────────────────────────────────────────

    @Test
    @DisplayName("AI 콜백 완료 — 유효한 AGENT 토큰과 react_document로 콜백하면 200과 COMPLETED를 반환한다")
    void complete() throws Exception {
        when(generationService.complete(any())).thenReturn(COMPLETED_RESPONSE);

        Map<String, Object> reactDocument = Map.of(
            "schemaVersion", "2.0",
            "canvasWidth", 774,
            "root", List.of()
        );

        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", reactDocument))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completedAt").exists())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "ai-callback-complete",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콜백 (내부)")
                    .summary("AI 생성 완료 콜백")
                    .description("AI 서버가 콘텐츠 생성을 완료한 뒤 react_document AST를 전송합니다.")
                    .pathParameters(parameterWithName("generationId").description("생성 요청 ID").type(SimpleType.INTEGER))
                    .requestFields(
                        subsectionWithPath("reactDocument").type(JsonFieldType.OBJECT)
                            .description("AI가 생성한 react_document AST (schemaVersion, canvasWidth, root[] 구조)")
                    )
                    .responseFields(successEnvelopeFields(GENERATION_FIELDS))
                    .build()
                )
            ));
    }

    // ── JSON 콜백 — 인증 실패 경우의 수 ─────────────────────────────────────

    @Test
    @DisplayName("AI 콜백 완료 인증 — Authorization 헤더가 없으면 401을 반환한다")
    void completeRejectsWithNoAuthHeader() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AI 콜백 완료 인증 — Bearer 접두사 없는 토큰은 401을 반환한다")
    void completeRejectsWithNoBearerPrefix() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .header(HttpHeaders.AUTHORIZATION, AGENT_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AI 콜백 완료 인증 — 잘못된 토큰 값은 401을 반환한다")
    void completeRejectsWithWrongToken() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AI 콜백 완료 인증 — ROLE_USER JWT 세션이 있어도 AGENT Bearer 헤더가 없으면 401을 반환한다")
    @WithMockUser(roles = "USER")
    void completeRejectsJwtUser() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AI 콜백 완료 인증 — ROLE_ARTISAN JWT 세션이 있어도 AGENT Bearer 헤더가 없으면 401을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void completeRejectsJwtArtisan() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AI 콜백 완료 인증 — ROLE_ADMIN JWT 세션이 있어도 AGENT Bearer 헤더가 없으면 401을 반환한다")
    @WithMockUser(roles = "ADMIN")
    void completeRejectsJwtAdmin() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isUnauthorized());
    }

    // ── JSON 콜백 — 비즈니스 에러 ────────────────────────────────────────────

    @Test
    @DisplayName("AI 콜백 완료 — react_document가 없으면 400 Bad Request를 반환한다")
    void completeMissingReactDocument() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andDo(documentError("ai-callback-complete-invalid", "AI 콜백 (내부)", "AI 생성 완료 콜백 — reactDocument 누락", "reactDocument 필드가 없는 경우입니다."));
    }

    @Test
    @DisplayName("AI 콜백 완료 — 존재하지 않는 generationId로 콜백하면 404를 반환한다")
    void completeNotFound() throws Exception {
        when(generationService.complete(any())).thenThrow(new NotFoundException("콘텐츠 생성 요청을 찾을 수 없습니다"));

        mockMvc.perform(post("/internal/generations/{generationId}/complete", 999L)
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())))))
            .andExpect(status().isNotFound())
            .andDo(documentError("ai-callback-complete-not-found", "AI 콜백 (내부)", "AI 생성 완료 콜백 — 없음", "존재하지 않는 generationId입니다."));
    }

    // ── 멀티파트 콜백 ──────────────────────────────────────────────────────────

    private static final BeToAiPersistAckResponse ACK_SAVED = new BeToAiPersistAckResponse(
        "1", "10", "SAVED", NOW.plusMinutes(1)
    );

    private static final BeToAiPersistAckResponse ACK_ALREADY_SAVED = new BeToAiPersistAckResponse(
        "1", "10", "ALREADY_SAVED", NOW.plusMinutes(1)
    );

    private static final org.springframework.restdocs.payload.FieldDescriptor[] ACK_FIELDS = {
        fieldWithPath("data.generation_id").type(JsonFieldType.STRING).description("생성 요청 ID"),
        fieldWithPath("data.product_id").type(JsonFieldType.STRING).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("처리 결과 (SAVED | ALREADY_SAVED)"),
        fieldWithPath("data.saved_at").type(JsonFieldType.STRING).optional().description("저장 완료 시각"),
    };

    @Test
    @DisplayName("멀티파트 콜백 — 유효한 AGENT 토큰으로 metadata와 이미지 파일을 전송하면 200과 SAVED를 반환한다")
    void completeMultipart() throws Exception {
        when(generationService.completeWithImages(any(), any(), any(), any(), any())).thenReturn(ACK_SAVED);

        String metadataJson = json(Map.of(
            "generationId", "1",
            "productId", "10",
            "detailPage", Map.of(
                "reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of())
            )
        ));

        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, metadataJson.getBytes());
        MockMultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/internal/generations/complete/multipart")
                .file(metadata)
                .file(detailImage)
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT)
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SAVED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "ai-callback-complete-multipart",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콜백 (내부)")
                    .summary("AI 생성 완료 콜백 — 멀티파트")
                    .description("AI 서버가 react_document JSON과 이미지 파일을 multipart/form-data로 전송합니다. " +
                        "Idempotency-Key 헤더로 중복 요청을 방지합니다.")
                    .responseFields(successEnvelopeFields(ACK_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("멀티파트 콜백 — 동일한 Idempotency-Key로 재요청하면 200과 ALREADY_SAVED를 반환한다")
    void completeMultipartIdempotent() throws Exception {
        when(generationService.completeWithImages(any(), any(), any(), any(), any())).thenReturn(ACK_ALREADY_SAVED);

        String metadataJson = json(Map.of(
            "generationId", "1",
            "productId", "10",
            "detailPage", Map.of("reactDocument", Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", List.of()))
        ));

        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, metadataJson.getBytes());
        MockMultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/internal/generations/complete/multipart")
                .file(metadata)
                .file(detailImage)
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT)
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ALREADY_SAVED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "ai-callback-complete-multipart-idempotent",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콜백 (내부)")
                    .summary("AI 생성 완료 콜백 — 멀티파트 중복 요청")
                    .description("이미 처리된 Idempotency-Key로 재요청하면 ALREADY_SAVED를 반환합니다.")
                    .responseFields(successEnvelopeFields(ACK_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("멀티파트 콜백 인증 — Authorization 헤더 없으면 401을 반환한다")
    void completeMultipartRejectsWithNoAuthHeader() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        MockMultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/internal/generations/complete/multipart")
                .file(metadata)
                .file(detailImage)
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("멀티파트 콜백 인증 — 잘못된 AGENT 토큰은 401을 반환한다")
    void completeMultipartRejectsWithWrongToken() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        MockMultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/internal/generations/complete/multipart")
                .file(metadata)
                .file(detailImage)
                .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("멀티파트 콜백 인증 — ROLE_USER JWT 세션이 있어도 AGENT Bearer 헤더가 없으면 401을 반환한다")
    @WithMockUser(roles = "USER")
    void completeMultipartRejectsJwtUser() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        MockMultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/internal/generations/complete/multipart")
                .file(metadata)
                .file(detailImage)
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isUnauthorized());
    }
}
