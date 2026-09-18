package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import java.util.LinkedHashMap;
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

    private static Map<String, Object> orderItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderItemId", 10L);
        item.put("productId", 5L);
        item.put("productName", "청자 다완");
        item.put("price", 85000);
        item.put("quantity", 1);
        item.put("thumbnail", List.of(Map.of("url", "https://cdn.midam.store/products/abc.jpg")));
        return item;
    }

    private static Map<String, Object> orderListItem() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", 1L);
        order.put("orderNumber", "ORD20260101001");
        order.put("status", "RETURN_REQUESTED");
        order.put("totalAmount", 85000);
        order.put("createdAt", "2026-09-01T10:00:00");
        order.put("items", List.of(orderItem()));
        Map<String, Object> returnInfo = new LinkedHashMap<>();
        returnInfo.put("type", "EXCHANGE");
        returnInfo.put("status", "REQUESTED");
        order.put("returnInfo", returnInfo);
        return order;
    }

    private static Map<String, Object> orderDetailItem() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", 1L);
        order.put("orderNumber", "ORD20260101001");
        order.put("status", "RETURN_REQUESTED");
        order.put("totalAmount", 85000);
        order.put("createdAt", "2026-09-01T10:00:00");
        order.put("items", List.of(orderItem()));
        Map<String, Object> returnInfo = new LinkedHashMap<>();
        returnInfo.put("type", "RETURN");
        returnInfo.put("status", "APPROVED");
        order.put("returnInfo", returnInfo);
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("addressId", 3L);
        address.put("recipientName", "홍길동");
        address.put("phone", "01012345678");
        address.put("zipCode", "06000");
        address.put("address1", "서울 강남구 테헤란로 1");
        address.put("address2", "101호");
        address.put("isDefault", false);
        order.put("address", address);
        return order;
    }

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

    private static FieldDescriptor[] orderItemFields(String prefix) {
        return new FieldDescriptor[]{
            fieldWithPath(prefix + "orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
            fieldWithPath(prefix + "productId").type(JsonFieldType.NUMBER).description("상품 ID"),
            fieldWithPath(prefix + "productName").type(JsonFieldType.STRING).description("상품명"),
            fieldWithPath(prefix + "price").type(JsonFieldType.NUMBER).description("단가"),
            fieldWithPath(prefix + "quantity").type(JsonFieldType.NUMBER).description("수량"),
            fieldWithPath(prefix + "thumbnail").type(JsonFieldType.ARRAY).description("썸네일 목록. 이미지 없으면 빈 배열"),
            fieldWithPath(prefix + "thumbnail[].url").type(JsonFieldType.STRING).optional().description("썸네일 URL"),
        };
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
    @DisplayName("주문 목록 조회 — 유저의 주문 목록과 상품/returnInfo를 반환한다")
    @WithMockUser(roles = "USER")
    void orders() throws Exception {
        Page<Map<String, Object>> page = new PageImpl<>(List.of(orderListItem()), PageRequest.of(0, 20), 1L);
        when(queries.orders(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/me/orders")
                .param("page", "0")
                .param("size", "20")
                .param("status", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].items").isArray())
            .andExpect(jsonPath("$.data.content[0].returnInfo.type").value("EXCHANGE"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-order-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 목록 조회")
                    .description("""
                        유저의 주문 목록을 페이지네이션으로 반환합니다.
                        status 허용값: ALL · CREATED(결제대기) · PAID(결제완료) · PAYMENT_FAILED(결제실패) \
                        · CANCELED(취소) · IN_DELIVERY(배송중) · DELIVERED(배송완료) · RETURN_REQUESTED(교환/환불신청)
                        returnInfo: status=RETURN_REQUESTED인 주문에만 포함됩니다.
                        returnInfo.type — RETURN(환불) | EXCHANGE(교환)
                        returnInfo.status — REQUESTED(신청) | APPROVED(승인) | REJECTED(불가) | COMPLETED(완료)""")
                    .queryParameters(
                        parameterWithName("status").description("주문 상태 필터 (기본: ALL)").optional(),
                        parameterWithName("from").description("조회 시작일 (ISO 8601 date, 예: 2026-01-01)").optional(),
                        parameterWithName("to").description("조회 종료일 (ISO 8601 date, 예: 2026-09-18)").optional(),
                        parameterWithName("artisanName").description("장인명 부분 일치 검색").optional(),
                        parameterWithName("page").description("페이지 번호 (기본: 0)").optional(),
                        parameterWithName("size").description("페이지 크기 (기본: 20)").optional()
                    )
                    .responseFields(successEnvelopeFields(withPageFields(
                        fieldWithPath("data.content").type(JsonFieldType.ARRAY).description("주문 목록"),
                        fieldWithPath("data.content[].orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.content[].orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.content[].status").type(JsonFieldType.STRING)
                            .description("주문 상태. CREATED(결제대기) | PAID(결제완료) | PAYMENT_FAILED(결제실패) | CANCELED(취소) | IN_DELIVERY(배송중) | DELIVERED(배송완료) | RETURN_REQUESTED(교환/환불신청)"),
                        fieldWithPath("data.content[].totalAmount").type(JsonFieldType.NUMBER).description("총 결제 금액"),
                        fieldWithPath("data.content[].createdAt").type(JsonFieldType.STRING).description("주문 생성일시"),
                        fieldWithPath("data.content[].items").type(JsonFieldType.ARRAY).description("주문 상품 목록"),
                        fieldWithPath("data.content[].items[].orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
                        fieldWithPath("data.content[].items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.content[].items[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.content[].items[].price").type(JsonFieldType.NUMBER).description("단가"),
                        fieldWithPath("data.content[].items[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                        fieldWithPath("data.content[].items[].thumbnail").type(JsonFieldType.ARRAY).description("썸네일 목록. 이미지 없으면 빈 배열"),
                        fieldWithPath("data.content[].items[].thumbnail[].url").type(JsonFieldType.STRING).optional().description("썸네일 URL"),
                        fieldWithPath("data.content[].returnInfo").type(JsonFieldType.OBJECT).optional()
                            .description("교환/환불 정보. RETURN_REQUESTED 상태인 주문에만 포함"),
                        fieldWithPath("data.content[].returnInfo.type").type(JsonFieldType.STRING).optional()
                            .description("유형. RETURN(환불) | EXCHANGE(교환)"),
                        fieldWithPath("data.content[].returnInfo.status").type(JsonFieldType.STRING).optional()
                            .description("처리 상태. REQUESTED(신청) | APPROVED(승인) | REJECTED(불가) | COMPLETED(완료)")
                    )))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("주문 목록 조회 — 허용되지 않는 status 값이면 400을 반환한다")
    @WithMockUser(roles = "USER")
    void ordersBadStatus() throws Exception {
        when(queries.orders(any(), any(), eq("INVALID"), any(), any(), any()))
            .thenThrow(new com.jangingmall.backend.global.exception.DomainException(
                com.jangingmall.backend.global.exception.ErrorCode.INVALID_INPUT));

        mockMvc.perform(get("/api/member/me/orders").param("status", "INVALID"))
            .andExpect(status().isBadRequest())
            .andDo(documentError("member-order-list-bad-status", "주문", "주문 목록 — 잘못된 status",
                "허용되지 않는 status 값이면 400을 반환합니다."));
    }

    @Test
    @DisplayName("주문 목록 조회 — ARTISAN 역할이면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void ordersForbidden() throws Exception {
        mockMvc.perform(get("/api/member/me/orders"))
            .andExpect(status().isForbidden())
            .andDo(documentError("member-order-list-forbidden", "주문", "주문 목록 — 권한 없음",
                "USER 역할이 없으면 403을 반환합니다."));
    }

    @Test
    @DisplayName("주문 상세 조회 — 유저의 특정 주문 상세를 반환한다")
    @WithMockUser(roles = "USER")
    void orderDetail() throws Exception {
        when(queries.order(any(), eq(1L))).thenReturn(orderDetailItem());

        mockMvc.perform(get("/api/member/me/orders/{orderId}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderId").value(1))
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.returnInfo.type").value("RETURN"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-order-detail",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 상세 조회")
                    .description("""
                        주문 ID로 특정 주문의 상세 정보를 반환합니다.
                        returnInfo: status=RETURN_REQUESTED인 주문에만 포함됩니다.
                        returnInfo.type — RETURN(환불) | EXCHANGE(교환)
                        returnInfo.status — REQUESTED(신청) | APPROVED(승인) | REJECTED(불가) | COMPLETED(완료)""")
                    .pathParameters(
                        parameterWithName("orderId").description("주문 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.orderId").type(JsonFieldType.NUMBER).description("주문 ID"),
                        fieldWithPath("data.orderNumber").type(JsonFieldType.STRING).description("주문번호"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING)
                            .description("주문 상태. CREATED(결제대기) | PAID(결제완료) | PAYMENT_FAILED(결제실패) | CANCELED(취소) | IN_DELIVERY(배송중) | DELIVERED(배송완료) | RETURN_REQUESTED(교환/환불신청)"),
                        fieldWithPath("data.totalAmount").type(JsonFieldType.NUMBER).description("총 결제 금액"),
                        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("주문 생성일시"),
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("주문 상품 목록"),
                        fieldWithPath("data.items[].orderItemId").type(JsonFieldType.NUMBER).description("주문 항목 ID"),
                        fieldWithPath("data.items[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.items[].productName").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.items[].price").type(JsonFieldType.NUMBER).description("단가"),
                        fieldWithPath("data.items[].quantity").type(JsonFieldType.NUMBER).description("수량"),
                        fieldWithPath("data.items[].thumbnail").type(JsonFieldType.ARRAY).description("썸네일 목록. 이미지 없으면 빈 배열"),
                        fieldWithPath("data.items[].thumbnail[].url").type(JsonFieldType.STRING).optional().description("썸네일 URL"),
                        fieldWithPath("data.returnInfo").type(JsonFieldType.OBJECT).optional()
                            .description("교환/환불 정보. RETURN_REQUESTED 상태인 주문에만 포함"),
                        fieldWithPath("data.returnInfo.type").type(JsonFieldType.STRING).optional()
                            .description("유형. RETURN(환불) | EXCHANGE(교환)"),
                        fieldWithPath("data.returnInfo.status").type(JsonFieldType.STRING).optional()
                            .description("처리 상태. REQUESTED(신청) | APPROVED(승인) | REJECTED(불가) | COMPLETED(완료)"),
                        fieldWithPath("data.address").type(JsonFieldType.OBJECT).description("배송지"),
                        fieldWithPath("data.address.addressId").type(JsonFieldType.NUMBER).description("배송지 ID"),
                        fieldWithPath("data.address.recipientName").type(JsonFieldType.STRING).description("수령인 이름"),
                        fieldWithPath("data.address.phone").type(JsonFieldType.STRING).description("수령인 전화번호"),
                        fieldWithPath("data.address.zipCode").type(JsonFieldType.STRING).description("우편번호"),
                        fieldWithPath("data.address.address1").type(JsonFieldType.STRING).description("주소"),
                        fieldWithPath("data.address.address2").type(JsonFieldType.STRING).optional().description("상세 주소"),
                        fieldWithPath("data.address.isDefault").type(JsonFieldType.BOOLEAN).description("기본 배송지 여부")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("주문 상세 조회 — 없는 주문이면 404를 반환한다")
    @WithMockUser(roles = "USER")
    void orderDetailNotFound() throws Exception {
        when(queries.order(any(), eq(999L)))
            .thenThrow(new com.jangingmall.backend.global.exception.DomainException(
                com.jangingmall.backend.global.exception.ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/member/me/orders/{orderId}", 999L))
            .andExpect(status().isNotFound())
            .andDo(documentError("member-order-detail-not-found", "주문", "주문 상세 — 없는 주문",
                "존재하지 않는 주문 ID이거나 본인 주문이 아니면 404를 반환합니다."));
    }

    @Test
    @DisplayName("주문 상태별 집계 조회 — 최근 3개월 주문 카운트를 반환한다")
    @WithMockUser(roles = "USER")
    void orderSummary() throws Exception {
        Map<String, Object> inProgress = new LinkedHashMap<>();
        inProgress.put("awaitingPayment", 1L);
        inProgress.put("preparing", 2L);
        inProgress.put("inDelivery", 1L);
        inProgress.put("delivered", 3L);
        Map<String, Object> closedCount = new LinkedHashMap<>();
        closedCount.put("returnOrExchange", 1L);
        closedCount.put("canceled", 2L);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inProgress", inProgress);
        summary.put("closedCount", closedCount);
        when(queries.orderCountSummary(any())).thenReturn(summary);

        mockMvc.perform(get("/api/member/me/orders/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inProgress.awaitingPayment").value(1))
            .andExpect(jsonPath("$.data.inProgress.inDelivery").value(1))
            .andExpect(jsonPath("$.data.closedCount.canceled").value(2))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "member-order-summary",
                resource(ResourceSnippetParameters.builder()
                    .tag("주문")
                    .summary("주문 상태별 집계 조회")
                    .description("최근 3개월 주문을 상태별로 집계합니다. 배송중(IN_DELIVERY)이 실제 카운트에 반영됩니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.inProgress").type(JsonFieldType.OBJECT).description("진행 중인 주문 집계"),
                        fieldWithPath("data.inProgress.awaitingPayment").type(JsonFieldType.NUMBER)
                            .description("결제 대기 (CREATED)"),
                        fieldWithPath("data.inProgress.preparing").type(JsonFieldType.NUMBER)
                            .description("상품 준비 중 (PAID)"),
                        fieldWithPath("data.inProgress.inDelivery").type(JsonFieldType.NUMBER)
                            .description("배송 중 (IN_DELIVERY)"),
                        fieldWithPath("data.inProgress.delivered").type(JsonFieldType.NUMBER)
                            .description("배송 완료 (DELIVERED)"),
                        fieldWithPath("data.closedCount").type(JsonFieldType.OBJECT).description("종결된 주문 집계"),
                        fieldWithPath("data.closedCount.returnOrExchange").type(JsonFieldType.NUMBER)
                            .description("교환/환불 신청 (RETURN_REQUESTED)"),
                        fieldWithPath("data.closedCount.canceled").type(JsonFieldType.NUMBER)
                            .description("취소 (CANCELED)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("주문 상태별 집계 조회 — ARTISAN 역할이면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void orderSummaryForbidden() throws Exception {
        mockMvc.perform(get("/api/member/me/orders/summary"))
            .andExpect(status().isForbidden())
            .andDo(documentError("member-order-summary-forbidden", "주문", "주문 집계 — 권한 없음",
                "USER 역할이 없으면 403을 반환합니다."));
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
        Map<String, Object> writableItem = new LinkedHashMap<>();
        writableItem.put("orderItemId", 5L);
        writableItem.put("productId", 2L);
        writableItem.put("productName", "청화백자 찻잔");
        writableItem.put("thumbnail", List.of());
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
