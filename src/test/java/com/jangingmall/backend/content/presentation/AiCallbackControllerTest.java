package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
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
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Map;

import com.epages.restdocs.apispec.SimpleType;

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
class AiCallbackControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private GenerationService generationService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0, 0);

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

    @Test
    @DisplayName("AI 콜백 완료 — react_document와 함께 콜백하면 200과 COMPLETED 상태를 반환한다")
    void complete() throws Exception {
        when(generationService.complete(any())).thenReturn(COMPLETED_RESPONSE);

        Map<String, Object> reactDocument = Map.of(
            "schemaVersion", "2.0",
            "canvasWidth", 774,
            "root", java.util.List.of()
        );

        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reactDocument", reactDocument))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completedAt").exists())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "ai-callback-complete",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콜백 (내부)")
                    .summary("AI 생성 완료 콜백")
                    .description("AI 서버가 콘텐츠 생성을 완료한 뒤 react_document AST를 전송합니다. 인증 불필요 — /internal/** 경로는 허용됩니다.")
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

    @Test
    @DisplayName("AI 콜백 완료 — react_document가 없으면 400 Bad Request를 반환한다")
    void completeMissingReactDocument() throws Exception {
        mockMvc.perform(post("/internal/generations/{generationId}/complete", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andDo(documentError("ai-callback-complete-invalid", "AI 콜백 (내부)", "AI 생성 완료 콜백 — reactDocument 누락", "reactDocument 필드가 없는 경우입니다."));
    }

    @Test
    @DisplayName("AI 콜백 완료 — 존재하지 않는 generationId로 콜백하면 404를 반환한다")
    void completeNotFound() throws Exception {
        when(generationService.complete(any())).thenThrow(new NotFoundException("콘텐츠 생성 요청을 찾을 수 없습니다"));

        Map<String, Object> reactDocument = Map.of("schemaVersion", "2.0", "canvasWidth", 774, "root", java.util.List.of());

        mockMvc.perform(post("/internal/generations/{generationId}/complete", 999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reactDocument", reactDocument))))
            .andExpect(status().isNotFound())
            .andDo(documentError("ai-callback-complete-not-found", "AI 콜백 (내부)", "AI 생성 완료 콜백 — 없음", "존재하지 않는 generationId입니다."));
    }
}
