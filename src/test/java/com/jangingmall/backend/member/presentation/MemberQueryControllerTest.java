package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.member.application.MemberQueryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(MemberQueryController.class)
@Import(SecurityConfig.class)
class MemberQueryControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private MemberQueryService queries;

    private static final Map<String, Object> WISH_ITEM = Map.of(
        "productId", 1L,
        "name", "청자 다완",
        "price", 85000,
        "status", "ON_SALE",
        "artisanId", 10L,
        "artisanName", "김도공 도예"
    );

    private static final Map<String, Object> ORDER_ITEM = Map.of(
        "orderId", 1L,
        "orderNumber", "ORD202601010001",
        "status", "PAID",
        "totalAmount", 85000
    );

    private static final Map<String, Object> REVIEW_ITEM = Map.of(
        "reviewId", 1L,
        "productId", 1L,
        "productName", "청자 다완",
        "rating", 5,
        "content", "정말 아름다운 작품입니다."
    );

    private static FieldDescriptor[] withPageFields(FieldDescriptor... contentFields) {
        FieldDescriptor[] pageFields = {
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
            fieldWithPath("data.pageable.offset").type(JsonFieldType.NUMBER).optional().description("오프셋"),
            fieldWithPath("data.pageable.pageNumber").type(JsonFieldType.NUMBER).optional().description("페이지 번호"),
            fieldWithPath("data.pageable.pageSize").type(JsonFieldType.NUMBER).optional().description("페이지 크기"),
            fieldWithPath("data.pageable.paged").type(JsonFieldType.BOOLEAN).optional().description("페이징 여부"),
            fieldWithPath("data.pageable.unpaged").type(JsonFieldType.BOOLEAN).optional().description("비페이징 여부"),
            fieldWithPath("data.pageable.sort.sorted").type(JsonFieldType.BOOLEAN).optional().description("정렬 여부"),
            fieldWithPath("data.pageable.sort.unsorted").type(JsonFieldType.BOOLEAN).optional().description("비정렬 여부"),
            fieldWithPath("data.pageable.sort.empty").type(JsonFieldType.BOOLEAN).optional().description("정렬 정보 없음"),
        };
        FieldDescriptor[] merged = new FieldDescriptor[contentFields.length + pageFields.length];
        System.arraycopy(contentFields, 0, merged, 0, contentFields.length);
        System.arraycopy(pageFields, 0, merged, contentFields.length, pageFields.length);
        return merged;
    }

    @Test
    @DisplayName("찜 목록 조회 — 유저가 찜한 상품 목록을 페이지로 반환한다")
    @WithMockUser(roles = "USER")
    void wishes() throws Exception {
        Page<Map<String, Object>> page = new PageImpl<>(List.of(WISH_ITEM), PageRequest.of(0, 20), 1L);
        when(queries.wishes(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/me/wishes")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-wish-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("찜")
                    .summary("찜 목록 조회")
                    .description("유저가 찜한 상품 목록을 페이지네이션으로 반환합니다.")
                    .queryParameters(
                        parameterWithName("page").description("페이지 번호 (0부터, 기본: 0)").optional(),
                        parameterWithName("size").description("페이지 크기 (기본: 20)").optional()
                    )
                    .responseFields(successEnvelopeFields(withPageFields(
                        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("찜 목록"),
                        fieldWithPath("data.content[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.content[].name").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.content[].price").type(JsonFieldType.NUMBER).description("가격"),
                        fieldWithPath("data.content[].status").type(JsonFieldType.STRING).description("상태"),
                        fieldWithPath("data.content[].artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.content[].artisanName").type(JsonFieldType.STRING).description("장인 이름")
                    )))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("찜 단건 조회 — 찜한 상품이면 204를 반환한다")
    @WithMockUser(roles = "USER")
    void isWished() throws Exception {
        when(queries.isWished(any(), eq(1L))).thenReturn(true);

        mockMvc.perform(get("/api/member/me/wishes/{productId}", 1L))
            .andExpect(status().isNoContent())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-wish-check",
                resource(ResourceSnippetParameters.builder()
                    .tag("찜")
                    .summary("찜 여부 조회")
                    .description("특정 상품의 찜 여부를 반환합니다. 찜한 경우 204, 찜하지 않은 경우 404를 반환합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER)
                    )
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("찜 단건 조회 — 찜하지 않은 상품이면 404를 반환한다")
    @WithMockUser(roles = "USER")
    void isNotWished() throws Exception {
        when(queries.isWished(any(), eq(99L))).thenReturn(false);

        mockMvc.perform(get("/api/member/me/wishes/{productId}", 99L))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("찜 목록 조회 — ARTISAN 역할이면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void wishesForbidden() throws Exception {
        mockMvc.perform(get("/api/member/me/wishes"))
            .andExpect(status().isForbidden())
            .andDo(documentError("member-wish-list-forbidden", "찜", "찜 목록 — 권한 없음", "USER 역할이 없으면 403을 반환합니다."));
    }

    @Test
    @DisplayName("주문 목록 조회 — 유저의 주문 목록을 페이지로 반환한다")
    @WithMockUser(roles = "USER")
    void orders() throws Exception {
        Page<Map<String, Object>> page = new PageImpl<>(List.of(ORDER_ITEM), PageRequest.of(0, 20), 1L);
        when(queries.orders(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/me/orders")
                .param("page", "0")
                .param("size", "20")
                .param("status", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-order-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 목록 조회")
                    .description("유저의 주문 목록을 페이지네이션으로 반환합니다. status: ALL, PAID, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED")
                    .queryParameters(
                        parameterWithName("status").description("주문 상태 필터 (기본: ALL)").optional(),
                        parameterWithName("page").description("페이지 번호 (기본: 0)").optional(),
                        parameterWithName("size").description("페이지 크기 (기본: 20)").optional()
                    )
                    .responseFields(successEnvelopeFields(withPageFields(
                        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("주문 목록"),
                        fieldWithPath("data.content[].orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.content[].orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.content[].status").type(JsonFieldType.STRING).description("주문 상태"),
                        fieldWithPath("data.content[].totalAmount").type(JsonFieldType.NUMBER).description("총 결제 금액")
                    )))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("주문 상세 조회 — 유저의 특정 주문 상세를 반환한다")
    @WithMockUser(roles = "USER")
    void orderDetail() throws Exception {
        Map<String, Object> detail = Map.of(
            "orderId", 1L,
            "orderNumber", "ORD202601010001",
            "status", "PAID",
            "totalAmount", 85000,
            "items", List.of()
        );
        when(queries.order(any(), eq(1L))).thenReturn(detail);

        mockMvc.perform(get("/api/member/me/orders/{orderId}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-order-detail",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 상세 조회")
                    .description("주문 ID로 특정 주문의 상세 정보를 반환합니다.")
                    .pathParameters(
                        parameterWithName("orderId").description("주문 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("주문 상태"),
                        fieldWithPath("data.totalAmount").type(JsonFieldType.NUMBER).description("총 결제 금액"),
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("주문 상품 목록")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("작성한 리뷰 목록 조회 — 유저가 작성한 리뷰를 페이지로 반환한다")
    @WithMockUser(roles = "USER")
    void reviews() throws Exception {
        Page<Map<String, Object>> page = new PageImpl<>(List.of(REVIEW_ITEM), PageRequest.of(0, 20), 1L);
        when(queries.reviews(any(), any(), eq(false))).thenReturn(page);

        mockMvc.perform(get("/api/member/me/reviews")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-review-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("리뷰")
                    .summary("작성한 리뷰 목록")
                    .description("유저가 작성한 리뷰 목록을 페이지네이션으로 반환합니다.")
                    .queryParameters(
                        parameterWithName("page").description("페이지 번호 (기본: 0)").optional(),
                        parameterWithName("size").description("페이지 크기 (기본: 20)").optional()
                    )
                    .responseFields(successEnvelopeFields(withPageFields(
                        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("리뷰 목록"),
                        fieldWithPath("data.content[].reviewId").type(JsonFieldType.NUMBER).description("리뷰 ID"),
                        fieldWithPath("data.content[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.content[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.content[].rating").type(JsonFieldType.NUMBER).description("평점"),
                        fieldWithPath("data.content[].content").type(JsonFieldType.STRING).description("리뷰 내용")
                    )))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("작성 가능한 리뷰 목록 조회 — 구매 후 아직 리뷰를 쓰지 않은 목록을 반환한다")
    @WithMockUser(roles = "USER")
    void writableReviews() throws Exception {
        Map<String, Object> writableItem = Map.of(
            "orderItemId", 5L,
            "productId", 2L,
            "productName", "청화백자 찻잔",
            "thumbnail", List.of()
        );
        Page<Map<String, Object>> page = new PageImpl<>(List.of(writableItem), PageRequest.of(0, 20), 1L);
        when(queries.reviews(any(), any(), eq(true))).thenReturn(page);

        mockMvc.perform(get("/api/member/me/reviews/writable")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-review-writable",
                resource(ResourceSnippetParameters.builder()
                    .tag("리뷰")
                    .summary("작성 가능한 리뷰 목록")
                    .description("구매 완료 후 아직 리뷰를 작성하지 않은 상품 목록을 반환합니다.")
                    .queryParameters(
                        parameterWithName("page").description("페이지 번호 (기본: 0)").optional(),
                        parameterWithName("size").description("페이지 크기 (기본: 20)").optional()
                    )
                    .responseFields(successEnvelopeFields(withPageFields(
                        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("작성 가능한 리뷰 목록"),
                        fieldWithPath("data.content[].orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
                        fieldWithPath("data.content[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.content[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.content[].thumbnail").type(JsonFieldType.ARRAY).description("썸네일 목록")
                    )))
                    .build()
                )
            ));
    }
}
