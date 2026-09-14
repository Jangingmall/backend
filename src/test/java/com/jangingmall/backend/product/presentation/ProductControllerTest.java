package com.jangingmall.backend.product.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.member.application.MemberActivityService;
import com.jangingmall.backend.product.application.ProductResponse;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private MemberActivityService memberActivityService;

    private static final ProductResponse SAMPLE = new ProductResponse(
        1L, 10L, 1L, "도자기", 2L, "청자", "청자 다완", "고려 청자 다완", 85000, 10,
        "https://example.com/thumb.jpg", "DRAFT",
        LocalDateTime.of(2026, 9, 3, 10, 0), LocalDateTime.of(2026, 9, 3, 10, 0)
    );

    private static final org.springframework.restdocs.payload.FieldDescriptor[] PRODUCT_FIELDS = {
        fieldWithPath("data.content[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.content[].artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
        fieldWithPath("data.content[].categoryId").type(JsonFieldType.NUMBER).optional().description("카테고리 ID"),
        fieldWithPath("data.content[].categoryName").type(JsonFieldType.STRING).optional().description("카테고리명"),
        fieldWithPath("data.content[].subcategoryId").type(JsonFieldType.NUMBER).optional().description("서브카테고리 ID"),
        fieldWithPath("data.content[].subcategoryName").type(JsonFieldType.STRING).optional().description("서브카테고리명"),
        fieldWithPath("data.content[].title").type(JsonFieldType.STRING).description("제목"),
        fieldWithPath("data.content[].description").type(JsonFieldType.STRING).optional().description("설명"),
        fieldWithPath("data.content[].price").type(JsonFieldType.NUMBER).description("가격"),
        fieldWithPath("data.content[].stock").type(JsonFieldType.NUMBER).description("재고"),
        fieldWithPath("data.content[].thumbnailUrl").type(JsonFieldType.STRING).optional().description("썸네일 URL"),
        fieldWithPath("data.content[].status").type(JsonFieldType.STRING).description("상태 (DRAFT/ON_SALE/SOLD_OUT/HIDDEN)"),
        fieldWithPath("data.content[].createdAt").type(JsonFieldType.STRING).description("생성일시"),
        fieldWithPath("data.content[].updatedAt").type(JsonFieldType.STRING).description("수정일시"),
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
        fieldWithPath("data.pageable").type(JsonFieldType.VARIES).description("페이지 요청 정보").optional(),
    };

    private static final org.springframework.restdocs.payload.FieldDescriptor[] SINGLE_PRODUCT_FIELDS = {
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
        fieldWithPath("data.categoryId").type(JsonFieldType.NUMBER).optional().description("카테고리 ID"),
        fieldWithPath("data.categoryName").type(JsonFieldType.STRING).optional().description("카테고리명"),
        fieldWithPath("data.subcategoryId").type(JsonFieldType.NUMBER).optional().description("서브카테고리 ID"),
        fieldWithPath("data.subcategoryName").type(JsonFieldType.STRING).optional().description("서브카테고리명"),
        fieldWithPath("data.title").type(JsonFieldType.STRING).description("제목"),
        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("설명"),
        fieldWithPath("data.price").type(JsonFieldType.NUMBER).description("가격"),
        fieldWithPath("data.stock").type(JsonFieldType.NUMBER).description("재고"),
        fieldWithPath("data.thumbnailUrl").type(JsonFieldType.STRING).optional().description("썸네일 URL"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("상태"),
        fieldWithPath("data.createdAt").type(JsonFieldType.STRING).description("생성일시"),
        fieldWithPath("data.updatedAt").type(JsonFieldType.STRING).description("수정일시"),
    };

    @Test
    @DisplayName("상품 등록 — 장인이 신규 상품을 DRAFT 상태로 등록한다")
    @WithMockUser(roles = "ARTISAN")
    void createProduct() throws Exception {
        when(productService.create(any())).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductRequest.Create(1L, 2L, "청자 다완", "고려 청자 다완", 85000, 10, "https://example.com/thumb.jpg"))))
            .andExpect(status().isCreated())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-create",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("상품 등록")
                    .description("장인이 신규 상품을 DRAFT 상태로 등록합니다.")
                    .requestFields(
                        fieldWithPath("categoryId").type(JsonFieldType.NUMBER).optional().description("카테고리 ID"),
                        fieldWithPath("subcategoryId").type(JsonFieldType.NUMBER).optional().description("서브카테고리 ID"),
                        fieldWithPath("title").type(JsonFieldType.STRING).description("상품명 (최대 200자)"),
                        fieldWithPath("description").type(JsonFieldType.STRING).optional().description("상품 설명"),
                        fieldWithPath("price").type(JsonFieldType.NUMBER).description("가격 (1 이상)"),
                        fieldWithPath("stock").type(JsonFieldType.NUMBER).description("재고 (0 이상)"),
                        fieldWithPath("thumbnailUrl").type(JsonFieldType.STRING).optional().description("썸네일 URL")
                    )
                    .responseFields(successEnvelopeFields(SINGLE_PRODUCT_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("내 상품 목록 조회 — 장인이 자신의 상품 목록을 페이징으로 조회한다")
    @WithMockUser(roles = "ARTISAN")
    void myProducts() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(SAMPLE));
        when(productService.findByArtisan(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/products/me"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-my-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("내 상품 목록 (장인)")
                    .description("로그인한 장인 본인의 상품 목록을 페이징으로 조회합니다.")
                    .responseFields(successEnvelopeFields(PRODUCT_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 전체 목록 조회 — ON_SALE 상품을 소비자가 조회한다")
    void list() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(SAMPLE));
        when(productService.findOnSale(any())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("상품 목록")
                    .description("판매 중(ON_SALE)인 상품 목록을 페이징으로 조회합니다.")
                    .responseFields(successEnvelopeFields(PRODUCT_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 상세 조회 — 상품 ID로 단건 조회한다")
    void detail() throws Exception {
        when(productService.findById(1L)).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/products/{productId}", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-detail",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("상품 상세")
                    .description("상품 ID로 단건 조회합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .responseFields(successEnvelopeFields(SINGLE_PRODUCT_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 수정 — 장인이 상품 정보를 수정한다")
    @WithMockUser(roles = "ARTISAN")
    void update() throws Exception {
        when(productService.update(any())).thenReturn(SAMPLE);

        mockMvc.perform(patch("/api/products/{productId}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductRequest.Update(1L, 2L, "청자 다완 (수정)", "수정된 설명", 90000, 8, "https://example.com/thumb2.jpg"))))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("상품 수정")
                    .description("장인이 본인 상품의 정보를 수정합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .requestFields(
                        fieldWithPath("categoryId").type(JsonFieldType.NUMBER).optional().description("카테고리 ID"),
                        fieldWithPath("subcategoryId").type(JsonFieldType.NUMBER).optional().description("서브카테고리 ID"),
                        fieldWithPath("title").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("description").type(JsonFieldType.STRING).optional().description("설명"),
                        fieldWithPath("price").type(JsonFieldType.NUMBER).description("가격"),
                        fieldWithPath("stock").type(JsonFieldType.NUMBER).description("재고"),
                        fieldWithPath("thumbnailUrl").type(JsonFieldType.STRING).optional().description("썸네일 URL")
                    )
                    .responseFields(successEnvelopeFields(SINGLE_PRODUCT_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 상태 변경 — DRAFT에서 ON_SALE로 전환한다")
    @WithMockUser(roles = "ARTISAN")
    void changeStatus() throws Exception {
        doNothing().when(productService).changeStatus(any());

        mockMvc.perform(patch("/api/products/{productId}/status", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductRequest.ChangeStatus("ON_SALE"))))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-change-status",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("상품 상태 변경")
                    .description("상품 상태를 변경합니다. 허용 전이: DRAFT→ON_SALE|HIDDEN, ON_SALE→SOLD_OUT|HIDDEN, SOLD_OUT→ON_SALE|HIDDEN, HIDDEN→ON_SALE|DRAFT")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .requestFields(
                        fieldWithPath("status").type(JsonFieldType.STRING).description("변경할 상태 (ON_SALE/SOLD_OUT/HIDDEN/DRAFT)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 삭제 — 장인이 본인 상품을 삭제한다")
    @WithMockUser(roles = "ARTISAN")
    void deleteProduct() throws Exception {
        doNothing().when(productService).delete(any(), any());

        mockMvc.perform(delete("/api/products/{productId}", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-delete",
                resource(ResourceSnippetParameters.builder()
                    .tag("상품")
                    .summary("상품 삭제")
                    .description("장인이 본인 상품을 삭제합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("상품 등록 — 비장인 계정이 등록 시도하면 403을 반환한다")
    @WithMockUser(roles = "USER")
    void createProductForbidden() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ProductRequest.Create(null, null, "청자 다완", null, 85000, 10, null))))
            .andExpect(status().isForbidden())
            .andDo(documentError(
                "product-create-forbidden",
                "상품",
                "상품 등록 — 권한 없음",
                "ARTISAN 역할이 없는 계정이 접근하면 403을 반환합니다."
            ));
    }

    @Test
    @DisplayName("찜 등록 — 유저가 상품을 찜 목록에 추가한다")
    @WithMockUser(roles = "USER")
    void wish() throws Exception {
        doNothing().when(memberActivityService).wish(any(), any());

        mockMvc.perform(post("/api/products/{productId}/wish", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-wish",
                resource(ResourceSnippetParameters.builder()
                    .tag("찜")
                    .summary("찜 등록")
                    .description("유저가 상품을 찜 목록에 추가합니다. 이미 찜한 상품은 중복 등록되지 않습니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("찜 취소 — 유저가 상품을 찜 목록에서 제거한다")
    @WithMockUser(roles = "USER")
    void unwish() throws Exception {
        doNothing().when(memberActivityService).unwish(any(), any());

        mockMvc.perform(delete("/api/products/{productId}/wish", 1L))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "product-unwish",
                resource(ResourceSnippetParameters.builder()
                    .tag("찜")
                    .summary("찜 취소")
                    .description("유저가 상품을 찜 목록에서 제거합니다. 찜하지 않은 상품을 취소해도 에러가 발생하지 않습니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID"))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data").type(JsonFieldType.NULL).description("데이터 없음")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("찜 등록 — 비회원(ARTISAN)이 찜 시도하면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void wishForbidden() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/wish", 1L))
            .andExpect(status().isForbidden())
            .andDo(documentError("product-wish-forbidden", "찜", "찜 등록 — 권한 없음", "USER 역할이 없으면 403을 반환합니다."));
    }
}
