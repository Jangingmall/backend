package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.jangingmall.backend.content.application.BeToAiPersistAckResponse;
import com.jangingmall.backend.content.application.GenerationService;
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

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
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

        mockMvc.perform(multipart("/internal/generations/{generationId}/completion", 1L)
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
                    .summary("AI 생성 완료 콜백")
                    .description("AI 서버가 react_document JSON과 이미지 파일을 multipart/form-data로 전송합니다.\n\n" +
                        "`Idempotency-Key` 헤더로 중복 요청을 방지합니다. " +
                        "동일한 키로 재요청하면 `status: ALREADY_SAVED`와 함께 200을 반환합니다 (409 없음).")
                    .pathParameters(parameterWithName("generationId").description("생성 요청 ID").type(SimpleType.INTEGER))
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

        mockMvc.perform(multipart("/internal/generations/{generationId}/completion", 1L)
                .file(metadata)
                .file(detailImage)
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT)
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ALREADY_SAVED"));
    }

    @Test
    @DisplayName("멀티파트 콜백 인증 — Authorization 헤더 없으면 401을 반환한다")
    void completeMultipartRejectsWithNoAuthHeader() throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        MockMultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/internal/generations/{generationId}/completion", 1L)
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

        mockMvc.perform(multipart("/internal/generations/{generationId}/completion", 1L)
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

        mockMvc.perform(multipart("/internal/generations/{generationId}/completion", 1L)
                .file(metadata)
                .file(detailImage)
                .header("Idempotency-Key", "unique-key-001"))
            .andExpect(status().isUnauthorized());
    }
}
