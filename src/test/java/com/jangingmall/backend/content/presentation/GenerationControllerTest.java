package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.content.application.GenerationResponse;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
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
import java.util.List;

import com.epages.restdocs.apispec.SimpleType;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GenerationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
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

    private static final GenerationResponse DRAFT_READY_RESPONSE = new GenerationResponse(
        1L, 10L, GenerationStatus.DRAFT_READY, NOW, null
    );

    private static final GenerationResponse FAILED_RESPONSE = new GenerationResponse(
        1L, 10L, GenerationStatus.FAILED, NOW, NOW.plusSeconds(8)
    );

    private static final org.springframework.restdocs.payload.FieldDescriptor[] GENERATION_FIELDS = {
        fieldWithPath("data.generationId").type(JsonFieldType.NUMBER).description("생성 요청 ID"),
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("생성 상태"),
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
                .content(json(new GenerationRequest.Create(List.of("imageId1", "imageId2"), "청자 다완", "손으로 직접 빚음", "물기 닦아서 보관"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.completedAt").doesNotExist())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "generation-request",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 상세페이지 생성 요청")
                    .description("상품 이미지·정보를 AI에 전송하여 상세페이지 콘텐츠 생성을 요청합니다. 즉시 202를 반환하고 생성은 비동기로 처리됩니다.\n\n"
                        + enumTable("GenerationStatus", entries(
                            "QUEUED", "대기 중",
                            "PROCESSING", "처리 중",
                            "ANALYZING", "분석 중",
                            "COMPLETED", "완료",
                            "FAILED", "실패"
                        )))
                    .pathParameters(parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER))
                    .requestFields(
                        fieldWithPath("images").type(JsonFieldType.ARRAY).description("S3 이미지 ID 목록 (최대 8장)"),
                        fieldWithPath("productName").type(JsonFieldType.STRING).description("작품명 (최대 15자)"),
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
                .content(json(new GenerationRequest.Create(List.of("imageId1"), "청자 다완", "손으로 빚음", "물 닦기"))))
            .andExpect(status().isForbidden())
            .andDo(documentError("generation-request-forbidden", "AI 콘텐츠 생성", "AI 생성 요청 — 권한 없음", "소유자가 아닌 장인이 요청한 경우입니다."));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — 이미지 9장 이상이면 400을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestTooManyImages() throws Exception {
        List<String> nineImages = List.of("img1", "img2", "img3", "img4", "img5", "img6", "img7", "img8", "img9");
        mockMvc.perform(post("/api/content/products/{productId}/generations", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new GenerationRequest.Create(nineImages, "청자 다완", "손으로 직접 빚음", "물기 닦아서 보관"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — 작품명 16자 이상이면 400을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestProductNameTooLong() throws Exception {
        mockMvc.perform(post("/api/content/products/{productId}/generations", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new GenerationRequest.Create(List.of("imageId1"), "일이삼사오육칠팔구십일이삼사오육", "손으로 직접 빚음", "물기 닦아서 보관"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
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
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 생성 상태 조회 (PROCESSING)")
                    .description("FE가 폴링으로 생성 진행 상태를 확인합니다. PROCESSING이면 계속 폴링, COMPLETED/FAILED면 종료합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("generationId").description("생성 요청 ID").type(SimpleType.INTEGER)
                    )
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
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 생성 상태 조회 (COMPLETED)")
                    .description("생성이 완료된 경우 COMPLETED와 completedAt을 반환합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("generationId").description("생성 요청 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(GENERATION_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 상태 조회 — FAILED 상태면 고정 대체 이미지 URL(fallbackImageUrl)을 함께 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void pollFailed() throws Exception {
        when(generationService.poll(anyLong(), anyLong(), any())).thenReturn(FAILED_RESPONSE);

        mockMvc.perform(get("/api/content/products/{productId}/generations/{generationId}", 10L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.fallbackImageUrl").value("http://test.webp"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "generation-poll-failed",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 생성 상태 조회 (FAILED)")
                    .description("요청 후 31분(데드라인) 안에 AI가 작업을 접수·완료하지 못했거나, AI 초안 이후 렌더링이 30분 안에 "
                        + "끝나지 않았거나, AI가 작업 실패를 확정한 경우 FAILED와 고정 대체 이미지 URL을 반환합니다. 데드라인 전에는 제출 오류(4xx 포함)가 나도 "
                        + "FAILED가 아니라 PROCESSING으로 대기하며 서버가 재제출합니다. "
                        + "fallbackImageUrl은 FAILED일 때만 포함됩니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("generationId").description("생성 요청 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.generationId").type(JsonFieldType.NUMBER).description("생성 요청 ID"),
                        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("생성 상태 (FAILED)"),
                        fieldWithPath("data.requestedAt").type(JsonFieldType.STRING).description("요청 시각"),
                        fieldWithPath("data.completedAt").type(JsonFieldType.STRING).optional().description("실패 확정 시각"),
                        fieldWithPath("data.fallbackImageUrl").type(JsonFieldType.STRING).description("FAILED일 때만 포함되는 고정 대체 이미지 URL")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 상태 조회 — FAILED가 아니면 fallbackImageUrl 필드가 없다")
    @WithMockUser(roles = "ARTISAN")
    void pollProcessingHasNoFallbackImageUrl() throws Exception {
        when(generationService.poll(anyLong(), anyLong(), any())).thenReturn(PROCESSING_RESPONSE);

        mockMvc.perform(get("/api/content/products/{productId}/generations/{generationId}", 10L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fallbackImageUrl").doesNotExist());
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

    @Test
    @DisplayName("AI 렌더링 수동 요청 — AI 초안이 준비된(DRAFT_READY) 생성 요청의 렌더링을 다시 요청하면 202 Accepted를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestRender() throws Exception {
        when(generationService.requestRender(anyLong(), anyLong(), any())).thenReturn(DRAFT_READY_RESPONSE);

        mockMvc.perform(post("/api/content/products/{productId}/generations/{generationId}/render", 10L, 1L))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("DRAFT_READY"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "generation-render",
                resource(ResourceSnippetParameters.builder()
                    .tag("AI 콘텐츠 생성")
                    .summary("AI 렌더링 수동 요청")
                    .description("AI 초안이 준비된(DRAFT_READY) 생성 요청의 최종 렌더링을 사용자가 수동으로 요청합니다. "
                        + "서버는 DRAFT_READY가 되면 자동으로 렌더링을 요청하고 30분 동안 주기적으로 재요청하며, "
                        + "이 API는 그 사이에 즉시 다시 요청하고 싶을 때 사용합니다. 같은 요청을 반복해도 중복 생성되지 않으며, "
                        + "즉시 202를 반환하고 렌더링은 비동기로 처리됩니다. 완료되면 상태가 COMPLETED가 됩니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("generationId").description("생성 요청 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(GENERATION_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("AI 렌더링 수동 요청 — DRAFT_READY가 아니면 422를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestRenderNotAllowed() throws Exception {
        when(generationService.requestRender(anyLong(), anyLong(), any()))
            .thenThrow(new BusinessRuleViolationException("AI 초안이 준비된 생성 요청만 렌더링을 요청할 수 있습니다"));

        mockMvc.perform(post("/api/content/products/{productId}/generations/{generationId}/render", 10L, 1L))
            .andExpect(status().isUnprocessableEntity())
            .andDo(documentError("generation-render-not-allowed", "AI 콘텐츠 생성", "AI 렌더링 수동 요청 — 요청 불가",
                "생성 요청이 DRAFT_READY 상태가 아닌 경우입니다."));
    }

    @Test
    @DisplayName("AI 렌더링 수동 요청 — 소유자가 아니면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestRenderForbidden() throws Exception {
        when(generationService.requestRender(anyLong(), anyLong(), any()))
            .thenThrow(new ForbiddenException("해당 상품에 대한 권한이 없습니다"));

        mockMvc.perform(post("/api/content/products/{productId}/generations/{generationId}/render", 10L, 1L))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — 제작 과정 2001자 이상이면 400을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestHowMadeTooLong() throws Exception {
        mockMvc.perform(post("/api/content/products/{productId}/generations", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new GenerationRequest.Create(List.of("imageId1"), "청자 다완", "가".repeat(2001), "물기 닦아서 보관"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("AI 콘텐츠 생성 요청 — 관리 방법 1001자 이상이면 400을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void requestCareTipsTooLong() throws Exception {
        mockMvc.perform(post("/api/content/products/{productId}/generations", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new GenerationRequest.Create(List.of("imageId1"), "청자 다완", "손으로 빚음", "가".repeat(1001)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }
}
