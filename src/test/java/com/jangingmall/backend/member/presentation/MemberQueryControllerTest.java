package com.jangingmall.backend.member.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.member.application.CursorPage;
import com.jangingmall.backend.member.application.MemberQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    @DisplayName("찜 목록 조회 — 유저가 찜한 상품 목록을 커서 페이지로 반환한다")
    @WithMockUser(roles = "USER")
    void wishes() throws Exception {
        CursorPage<Map<String, Object>> page = new CursorPage<>(List.of(WISH_ITEM), null, false, 1L);
        when(queries.wishes(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/me/wishes")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-wish-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("찜")
                    .summary("찜 목록 조회")
                    .description("유저가 찜한 상품 목록을 커서 페이지네이션으로 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("찜 목록"),
                        fieldWithPath("data.items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.items[].name").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.items[].price").type(JsonFieldType.NUMBER).description("가격"),
                        fieldWithPath("data.items[].status").type(JsonFieldType.STRING).description("상태"),
                        fieldWithPath("data.items[].artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.items[].artisanName").type(JsonFieldType.STRING).description("장인 이름"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서 (없으면 null)"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 찜 수")
                    ))
                    .build()
                )
            ));
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
    @DisplayName("주문 목록 조회 — 유저의 주문 목록을 커서 페이지로 반환한다")
    @WithMockUser(roles = "USER")
    void orders() throws Exception {
        CursorPage<Map<String, Object>> page = new CursorPage<>(List.of(ORDER_ITEM), null, false, 1L);
        when(queries.orders(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/me/orders")
                .param("limit", "20")
                .param("status", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-order-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 목록 조회")
                    .description("유저의 주문 목록을 커서 페이지네이션으로 반환합니다. status: ALL, PAID, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("주문 목록"),
                        fieldWithPath("data.items[].orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.items[].orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.items[].status").type(JsonFieldType.STRING).description("주문 상태"),
                        fieldWithPath("data.items[].totalAmount").type(JsonFieldType.NUMBER).description("총 결제 금액"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 주문 수")
                    ))
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
    @DisplayName("작성한 리뷰 목록 조회 — 유저가 작성한 리뷰를 커서 페이지로 반환한다")
    @WithMockUser(roles = "USER")
    void reviews() throws Exception {
        CursorPage<Map<String, Object>> page = new CursorPage<>(List.of(REVIEW_ITEM), null, false, 1L);
        when(queries.reviews(any(), any(), eq(false))).thenReturn(page);

        mockMvc.perform(get("/api/member/me/reviews")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-review-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("리뷰")
                    .summary("작성한 리뷰 목록")
                    .description("유저가 작성한 리뷰 목록을 커서 페이지네이션으로 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("리뷰 목록"),
                        fieldWithPath("data.items[].reviewId").type(JsonFieldType.NUMBER).description("리뷰 ID"),
                        fieldWithPath("data.items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.items[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.items[].rating").type(JsonFieldType.NUMBER).description("평점"),
                        fieldWithPath("data.items[].content").type(JsonFieldType.STRING).description("리뷰 내용"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 리뷰 수")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("작성 가능한 리뷰 목록 조회 — 구매 후 아직 리뷰를 쓰지 않은 목록을 반환한다")
    @WithMockUser(roles = "USER")
    void writableReviews() throws Exception {
        Map<String, Object> writableItem = Map.of(
            "reviewId", 0L,
            "productId", 2L,
            "productName", "청화백자 찻잔",
            "rating", 0,
            "content", ""
        );
        CursorPage<Map<String, Object>> page = new CursorPage<>(List.of(writableItem), null, false, 1L);
        when(queries.reviews(any(), any(), eq(true))).thenReturn(page);

        mockMvc.perform(get("/api/member/me/reviews/writable")
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-review-writable",
                resource(ResourceSnippetParameters.builder()
                    .tag("리뷰")
                    .summary("작성 가능한 리뷰 목록")
                    .description("구매 완료 후 아직 리뷰를 작성하지 않은 상품 목록을 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("작성 가능한 리뷰 목록"),
                        fieldWithPath("data.items[].reviewId").type(JsonFieldType.NUMBER).description("리뷰 ID (미작성: 0)"),
                        fieldWithPath("data.items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.items[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.items[].rating").type(JsonFieldType.NUMBER).description("평점 (미작성: 0)"),
                        fieldWithPath("data.items[].content").type(JsonFieldType.STRING).description("리뷰 내용 (미작성: 빈 문자열)"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 작성 가능 수")
                    ))
                    .build()
                )
            ));
    }
}
