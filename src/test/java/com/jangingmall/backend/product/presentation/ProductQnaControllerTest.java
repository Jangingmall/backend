package com.jangingmall.backend.product.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.member.application.MemberActivityService;
import com.jangingmall.backend.product.application.ProductQnaResponse;
import com.jangingmall.backend.product.application.ProductQnaService;
import com.jangingmall.backend.product.application.ProductReviewService;
import com.jangingmall.backend.product.application.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductQnaControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ProductQnaService productQnaService;
    @MockitoBean
    private ProductReviewService productReviewService;
    @MockitoBean
    private MemberActivityService memberActivityService;

    private static final ProductQnaResponse.QuestionView SAMPLE_QUESTION = new ProductQnaResponse.QuestionView(
        1L, 1L, 99L, "제품이 얼마나 튼튼한가요?", false, LocalDateTime.of(2026, 9, 3, 10, 0), null
    );

    private static final ProductQnaResponse.AnswerView SAMPLE_ANSWER = new ProductQnaResponse.AnswerView(
        1L, 10L, "매우 튼튼합니다.", LocalDateTime.of(2026, 9, 3, 11, 0)
    );

    @Test
    @DisplayName("상품 문의 목록 조회 — 공개 문의 목록을 페이징으로 조회한다")
    void questions() throws Exception {
        Page<ProductQnaResponse.QuestionView> page = new PageImpl<>(List.of(SAMPLE_QUESTION));
        when(productQnaService.findQuestions(anyLong(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/products/{productId}/questions", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-qna-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품 문의")
                    .summary("상품 문의 목록")
                    .description("상품의 문의 목록을 페이징으로 조회합니다. 비공개 문의는 마스킹됩니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.content[].questionId").type(JsonFieldType.NUMBER).description("문의 ID"),
                        fieldWithPath("data.content[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.content[].writerId").type(JsonFieldType.NUMBER).description("작성자 ID"),
                        fieldWithPath("data.content[].content").type(JsonFieldType.STRING).description("문의 내용 (비공개는 마스킹)"),
                        fieldWithPath("data.content[].secret").type(JsonFieldType.BOOLEAN).description("비공개 여부"),
                        fieldWithPath("data.content[].createdAt").type(JsonFieldType.STRING).description("작성일시"),
                        fieldWithPath("data.content[].answer").type(JsonFieldType.VARIES).optional().description("답변 (없으면 null)"),
                        fieldWithPath("data.totalElements").type(JsonFieldType.NUMBER).description("전체 수"),
                        fieldWithPath("data.totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수"),
                        fieldWithPath("data.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
                        fieldWithPath("data.number").type(JsonFieldType.NUMBER).description("현재 페이지"),
                        fieldWithPath("data.first").type(JsonFieldType.BOOLEAN).description("첫 페이지 여부"),
                        fieldWithPath("data.last").type(JsonFieldType.BOOLEAN).description("마지막 페이지 여부"),
                        fieldWithPath("data.empty").type(JsonFieldType.BOOLEAN).description("비어있는지 여부"),
                        fieldWithPath("data.numberOfElements").type(JsonFieldType.NUMBER).description("현재 페이지 요소 수"),
                        fieldWithPath("data.sort.sorted").type(JsonFieldType.BOOLEAN).description("정렬 여부"),
                        fieldWithPath("data.sort.unsorted").type(JsonFieldType.BOOLEAN).description("비정렬 여부"),
                        fieldWithPath("data.sort.empty").type(JsonFieldType.BOOLEAN).description("정렬 정보 없음"),
                        fieldWithPath("data.pageable").type(JsonFieldType.VARIES).optional().description("페이지 요청 정보")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 문의 등록 — 소비자가 문의를 등록한다")
    @WithMockUser(roles = "USER")
    void ask() throws Exception {
        when(productQnaService.ask(any())).thenReturn(SAMPLE_QUESTION);

        mockMvc.perform(post("/api/products/{productId}/questions", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductQnaRequest.Ask("제품이 얼마나 튼튼한가요?", false))))
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-qna-ask",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품 문의")
                    .summary("상품 문의 등록")
                    .description("소비자가 상품에 문의를 등록합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .requestFields(
                        fieldWithPath("content").type(JsonFieldType.STRING).description("문의 내용 (최대 1000자)"),
                        fieldWithPath("secret").type(JsonFieldType.BOOLEAN).description("비공개 여부")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.questionId").type(JsonFieldType.NUMBER).description("문의 ID"),
                        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.writerId").type(JsonFieldType.NUMBER).description("작성자 ID"),
                        fieldWithPath("data.content").type(JsonFieldType.STRING).description("문의 내용"),
                        fieldWithPath("data.secret").type(JsonFieldType.BOOLEAN).description("비공개 여부"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("작성일시"),
                        fieldWithPath("data.answer").type(JsonFieldType.VARIES).optional().description("답변")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 문의 답변 — 장인이 문의에 답변을 등록한다")
    @WithMockUser(roles = "ARTISAN")
    void answer() throws Exception {
        when(productQnaService.answer(any())).thenReturn(SAMPLE_ANSWER);

        mockMvc.perform(post("/api/products/{productId}/questions/{questionId}/answer", 1L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductQnaRequest.Answer("매우 튼튼합니다."))))
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-qna-answer",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품 문의")
                    .summary("문의 답변 등록")
                    .description("해당 상품의 장인이 문의에 답변을 등록합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID"),
                        parameterWithName("questionId").description("문의 ID")
                    )
                    .requestFields(
                        fieldWithPath("content").type(JsonFieldType.STRING).description("답변 내용 (최대 2000자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.answerId").type(JsonFieldType.NUMBER).description("답변 ID"),
                        fieldWithPath("data.artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.content").type(JsonFieldType.STRING).description("답변 내용"),
                        fieldWithPath("data.answeredAt").type(JsonFieldType.STRING).description("답변일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("문의 등록 — 소비자 권한 없으면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void ask_forbiddenForArtisan() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/questions", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductQnaRequest.Ask("질문", false))))
            .andExpect(status().isForbidden());
    }
}
