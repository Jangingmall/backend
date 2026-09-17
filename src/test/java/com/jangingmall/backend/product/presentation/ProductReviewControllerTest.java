package com.jangingmall.backend.product.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.member.application.MemberActivityService;
import com.jangingmall.backend.product.application.ProductQnaService;
import com.jangingmall.backend.product.application.ProductReviewResponse;
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

import com.epages.restdocs.apispec.SimpleType;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductReviewControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ProductQnaService productQnaService;
    @MockitoBean
    private ProductReviewService productReviewService;
    @MockitoBean
    private MemberActivityService memberActivityService;

    private static final ProductReviewResponse.ReviewView SAMPLE_REVIEW = new ProductReviewResponse.ReviewView(
        1L, 1L, 99L, 100L, (short) 5, "정말 만족합니다!", LocalDateTime.of(2026, 9, 3, 10, 0)
    );

    @Test
    @DisplayName("상품 후기 목록 조회 — 상품의 후기를 페이징으로 조회한다")
    void reviews() throws Exception {
        Page<ProductReviewResponse.ReviewView> page = new PageImpl<>(List.of(SAMPLE_REVIEW));
        when(productReviewService.findReviews(anyLong(), any())).thenReturn(page);

        mockMvc.perform(get("/api/products/{productId}/reviews", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-review-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품 후기")
                    .summary("상품 후기 목록")
                    .description("상품의 후기 목록을 페이징으로 조회합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.content[].reviewId").type(JsonFieldType.NUMBER).description("후기 ID"),
                        fieldWithPath("data.content[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.content[].writerId").type(JsonFieldType.NUMBER).description("작성자 ID"),
                        fieldWithPath("data.content[].orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
                        fieldWithPath("data.content[].rating").type(JsonFieldType.NUMBER).description("평점 (1~5)"),
                        fieldWithPath("data.content[].content").type(JsonFieldType.STRING).description("후기 내용"),
                        fieldWithPath("data.content[].createdAt").type(JsonFieldType.STRING).description("작성일시"),
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
    @DisplayName("상품 후기 등록 — 소비자가 후기를 등록한다")
    @WithMockUser(roles = "USER")
    void writeReview() throws Exception {
        when(productReviewService.write(any())).thenReturn(SAMPLE_REVIEW);

        mockMvc.perform(post("/api/products/{productId}/reviews", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductReviewRequest.Write(100L, (short) 5, "정말 만족합니다!"))))
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-review-write",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품 후기")
                    .summary("상품 후기 등록")
                    .description("소비자가 구매한 상품에 후기를 등록합니다. 동일 주문 항목에 중복 등록 불가합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER))
                    .requestFields(
                        fieldWithPath("orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
                        fieldWithPath("rating").type(JsonFieldType.NUMBER).description("평점 (1~5)"),
                        fieldWithPath("content").type(JsonFieldType.STRING).description("후기 내용 (최대 2000자)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.reviewId").type(JsonFieldType.NUMBER).description("후기 ID"),
                        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.writerId").type(JsonFieldType.NUMBER).description("작성자 ID"),
                        fieldWithPath("data.orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
                        fieldWithPath("data.rating").type(JsonFieldType.NUMBER).description("평점"),
                        fieldWithPath("data.content").type(JsonFieldType.STRING).description("후기 내용"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("작성일시")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("후기 등록 — 소비자 권한 없으면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void writeReview_forbiddenForArtisan() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/reviews", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductReviewRequest.Write(100L, (short) 5, "후기"))))
            .andExpect(status().isForbidden());
    }
}
