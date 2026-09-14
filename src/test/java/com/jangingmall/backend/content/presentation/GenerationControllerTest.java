package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.content.application.GenerationResponse;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenerationController.class)
@Import(SecurityConfig.class)
class GenerationControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private GenerationService generationService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0, 0);

    private static final GenerationResponse PROCESSING_RESPONSE = new GenerationResponse(
        1L, 10L, GenerationStatus.PROCESSING, NOW, null
    );

    private static final GenerationResponse COMPLETED_RESPONSE = new GenerationResponse(
        1L, 10L, GenerationStatus.COMPLETED, NOW, NOW.plusMinutes(1)
    );

    private static final org.springframework.restdocs.payload.FieldDescriptor[] GENERATION_FIELDS = {
        fieldWithPath("data.generationId").type(JsonFieldType.NUMBER).description("생성 요청 ID"),
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("생성 상태 (PROCESSING | COMPLETED | FAILED)"),
        fieldWithPath("data.requestedAt").type(JsonFieldType.STRING).description("요청 시각"),
        fieldWithPath("data.completedAt").type(JsonFieldType.STRING).optional().description("완료 시각 (PROCESSING이면 null)"),
    };

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — 장인이 상품 상세페이지 AI 생성을 요청하면 202 Accepted와 PROCESSING 상태를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void request() throws Exception {
        when(generationService.request(any())).thenReturn(PROCESSING_RESPONSE);

        mockMvc.perform(post("/api/content/products/{productId}/generations", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "images": ["imageId1", "imageId2"],
                      "productName": "청자 다완",
                      "howMade": "손으로 직접 빚음",
                      "careTips": "물기 닦아서 보관"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.completedAt").doesNotExist())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "generation-request",
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 상세페이지 생성 요청")
                    .description("상품 이미지·정보를 AI에 전송하여 상세페이지 콘텐츠 생성을 요청합니다. 즉시 202를 반환하고 생성은 비동기로 처리됩니다.")
                    .requestFields(
                        fieldWithPath("images").type(JsonFieldType.ARRAY).description("S3 이미지 ID 목록"),
                        fieldWithPath("productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("howMade").type(JsonFieldType.STRING).description("제작 과정"),
                        fieldWithPath("careTips").type(JsonFieldType.STRING).description("관리 방법")
                    )
                    .responseFields(successEnvelopeFields(GENERATION_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — 권한 없는 장인이 요청하면 403 Forbidden을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestForbidden() throws Exception {
        when(generationService.request(any())).thenThrow(new ForbiddenException("해당 상품에 대한 권한이 없습니다"));

        mockMvc.perform(post("/api/content/products/{productId}/generations", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "images": ["imageId1"],
                      "productName": "청자 다완",
                      "howMade": "손으로 빚음",
                      "careTips": "물 닦기"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andDo(documentError("generation-request-forbidden", "AI 콘텐츠 생성", "AI 생성 요청 — 권한 없음", "소유자가 아닌 장인이 요청한 경우입니다."));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 상태 조회 — PROCESSING 상태의 생성 요청을 폴링한다")
    @WithMockUser(roles = "ARTISAN")
    void pollProcessing() throws Exception {
        when(generationService.poll(anyLong(), anyLong(), any())).thenReturn(PROCESSING_RESPONSE);

        mockMvc.perform(get("/api/content/products/{productId}/generations/{generationId}", 10L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "generation-poll-processing",
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("generationId").description("생성 요청 ID")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 생성 상태 조회 (PROCESSING)")
                    .description("FE가 폴링으로 생성 진행 상태를 확인합니다. PROCESSING이면 계속 폴링, COMPLETED/FAILED면 종료합니다.")
                    .responseFields(successEnvelopeFields(GENERATION_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 상태 조회 — COMPLETED 상태를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void pollCompleted() throws Exception {
        when(generationService.poll(anyLong(), anyLong(), any())).thenReturn(COMPLETED_RESPONSE);

        mockMvc.perform(get("/api/content/products/{productId}/generations/{generationId}", 10L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completedAt").exists())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "generation-poll-completed",
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("generationId").description("생성 요청 ID")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 생성 상태 조회 (COMPLETED)")
                    .description("생성이 완료된 경우 COMPLETED와 completedAt을 반환합니다.")
                    .responseFields(successEnvelopeFields(GENERATION_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 상태 조회 — 존재하지 않는 생성 요청 조회 시 404를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void pollNotFound() throws Exception {
        when(generationService.poll(anyLong(), anyLong(), any())).thenThrow(new NotFoundException("콘텐츠 생성 요청을 찾을 수 없습니다"));

        mockMvc.perform(get("/api/content/products/{productId}/generations/{generationId}", 10L, 999L))
            .andExpect(status().isNotFound())
            .andDo(documentError("generation-poll-not-found", "AI 콘텐츠 생성", "AI 생성 상태 조회 — 없음", "존재하지 않는 generationId입니다."));
    }
}
