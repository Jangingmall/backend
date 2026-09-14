package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.content.application.InterviewResponse;
import com.jangingmall.backend.content.application.InterviewService;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.ConflictException;
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

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@Import(SecurityConfig.class)
class InterviewControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private InterviewService interviewService;

    private static final InterviewResponse SAMPLE = new InterviewResponse(
        10L, "손으로 직접 빚음", "청자", "청자기법", "300년 가문의 전통"
    );

    private static final org.springframework.restdocs.payload.FieldDescriptor[] INTERVIEW_FIELDS = {
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.process").type(JsonFieldType.STRING).description("제작 과정"),
        fieldWithPath("data.materials").type(JsonFieldType.STRING).description("소재"),
        fieldWithPath("data.technique").type(JsonFieldType.STRING).description("기법"),
        fieldWithPath("data.story").type(JsonFieldType.STRING).description("스토리"),
    };

    @Test
    @DisplayName("취재 데이터 등록 — 장인이 AI 생성을 위한 취재 데이터를 등록한다")
    @WithMockUser(roles = "ARTISAN")
    void create() throws Exception {
        when(interviewService.create(any())).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/content/products/{productId}/interview", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "process": "손으로 직접 빚음",
                      "materials": "청자",
                      "technique": "청자기법",
                      "story": "300년 가문의 전통"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.productId").value(10))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "interview-create",
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("취재 데이터 등록")
                    .description("AI 상세페이지 생성에 필요한 제작과정·소재·기법·스토리를 등록합니다. 상품당 1건만 허용됩니다.")
                    .requestFields(
                        fieldWithPath("process").type(JsonFieldType.STRING).description("제작 과정"),
                        fieldWithPath("materials").type(JsonFieldType.STRING).description("소재"),
                        fieldWithPath("technique").type(JsonFieldType.STRING).description("기법"),
                        fieldWithPath("story").type(JsonFieldType.STRING).description("스토리")
                    )
                    .responseFields(successEnvelopeFields(INTERVIEW_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("취재 데이터 등록 — 이미 등록된 상품에 재등록 시 409 Conflict를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void createConflict() throws Exception {
        when(interviewService.create(any())).thenThrow(new ConflictException("이미 취재 데이터가 등록된 상품입니다"));

        mockMvc.perform(post("/api/content/products/{productId}/interview", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "process": "과정",
                      "materials": "소재",
                      "technique": "기법",
                      "story": "스토리"
                    }
                    """))
            .andExpect(status().isConflict())
            .andDo(documentError("interview-create-conflict", "콘텐츠", "취재 데이터 등록 — 중복", "이미 취재 데이터가 존재하는 상품입니다."));
    }

    @Test
    @DisplayName("취재 데이터 등록 — 타인의 상품에 등록 시 403 Forbidden을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void createForbidden() throws Exception {
        when(interviewService.create(any())).thenThrow(new ForbiddenException("해당 상품에 대한 권한이 없습니다"));

        mockMvc.perform(post("/api/content/products/{productId}/interview", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "process": "과정",
                      "materials": "소재",
                      "technique": "기법",
                      "story": "스토리"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andDo(documentError("interview-create-forbidden", "콘텐츠", "취재 데이터 등록 — 권한 없음", "소유자가 아닌 장인이 접근한 경우입니다."));
    }

    @Test
    @DisplayName("취재 데이터 조회 — 장인이 등록한 취재 데이터를 조회한다")
    @WithMockUser(roles = "ARTISAN")
    void find() throws Exception {
        when(interviewService.find(any(), any())).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/content/products/{productId}/interview", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.productId").value(10))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "interview-find",
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("취재 데이터 조회")
                    .description("등록된 취재 데이터를 조회합니다.")
                    .responseFields(successEnvelopeFields(INTERVIEW_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("취재 데이터 조회 — 취재 데이터가 없으면 404 Not Found를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void findNotFound() throws Exception {
        when(interviewService.find(any(), any())).thenThrow(new NotFoundException("취재 데이터를 찾을 수 없습니다"));

        mockMvc.perform(get("/api/content/products/{productId}/interview", 10L))
            .andExpect(status().isNotFound())
            .andDo(documentError("interview-find-not-found", "콘텐츠", "취재 데이터 조회 — 없음", "취재 데이터가 등록되지 않은 상품입니다."));
    }

    @Test
    @DisplayName("취재 데이터 수정 — 장인이 취재 데이터를 부분 수정한다")
    @WithMockUser(roles = "ARTISAN")
    void update() throws Exception {
        InterviewResponse updated = new InterviewResponse(10L, "새 과정", "새 소재", "청자기법", "300년 가문의 전통");
        when(interviewService.update(any())).thenReturn(updated);

        mockMvc.perform(patch("/api/content/products/{productId}/interview", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "process": "새 과정",
                      "materials": "새 소재"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.process").value("새 과정"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "interview-update",
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("취재 데이터 수정")
                    .description("취재 데이터를 부분 수정합니다. 전달하지 않은 필드는 기존 값을 유지합니다.")
                    .requestFields(
                        fieldWithPath("process").type(JsonFieldType.STRING).optional().description("제작 과정 (선택)"),
                        fieldWithPath("materials").type(JsonFieldType.STRING).optional().description("소재 (선택)"),
                        fieldWithPath("technique").type(JsonFieldType.STRING).optional().description("기법 (선택)"),
                        fieldWithPath("story").type(JsonFieldType.STRING).optional().description("스토리 (선택)")
                    )
                    .responseFields(successEnvelopeFields(INTERVIEW_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("취재 데이터 수정 — 취재 데이터가 없으면 404 Not Found를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void updateNotFound() throws Exception {
        when(interviewService.update(any())).thenThrow(new NotFoundException("취재 데이터를 찾을 수 없습니다"));

        mockMvc.perform(patch("/api/content/products/{productId}/interview", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "process": "새 과정"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andDo(documentError("interview-update-not-found", "콘텐츠", "취재 데이터 수정 — 없음", "취재 데이터가 등록되지 않은 상품입니다."));
    }

    @Test
    @DisplayName("취재 데이터 수정 — 타인의 상품 수정 시 403 Forbidden을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void updateForbidden() throws Exception {
        when(interviewService.update(any())).thenThrow(new ForbiddenException("해당 상품에 대한 권한이 없습니다"));

        mockMvc.perform(patch("/api/content/products/{productId}/interview", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "process": "새 과정"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andDo(documentError("interview-update-forbidden", "콘텐츠", "취재 데이터 수정 — 권한 없음", "소유자가 아닌 장인이 접근한 경우입니다."));
    }
}
