package com.jangingmall.backend.product.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.product.application.ExhibitionQueryService;
import com.jangingmall.backend.product.application.ExhibitionResponse;
import com.jangingmall.backend.product.application.ProductResponse;
import com.jangingmall.backend.product.domain.ExhibitionProductSort;
import com.jangingmall.backend.product.domain.ExhibitionSort;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ExhibitionController.class)
@Import(SecurityConfig.class)
class ExhibitionControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ExhibitionQueryService exhibitionQueryService;

    private static final ProductResponse PRODUCT = new ProductResponse(
        8L, 22L, 1L, "공예", 10L, "조명", "대나무 조명", "가는 대나무를 엮어 만든 조명", 500000, 30,
        "https://example.com/bamboo.webp", "ON_SALE",
        LocalDateTime.of(2026, 9, 3, 10, 0), LocalDateTime.of(2026, 9, 3, 10, 0),
        List.of("EXHIBITION"), List.of(), 14, List.of("황토색"));

    private static String sortDescription(Class<? extends Enum<?>> type, java.util.function.Function<Enum<?>, String> describe) {
        return Arrays.stream(type.getEnumConstants()).map(value -> "  - " + value.name() + ": " + describe.apply(value))
            .collect(Collectors.joining("\n"));
    }

    private static String exhibitionSorts() {
        return sortDescription(ExhibitionSort.class, value -> ((ExhibitionSort) value).description());
    }

    private static String productSorts() {
        return sortDescription(ExhibitionProductSort.class, value -> ((ExhibitionProductSort) value).description());
    }

    @Test
    @DisplayName("기획전 목록 조회 — 노출 중인 기획전을 정렬 기준에 따라 조회한다")
    void list() throws Exception {
        when(exhibitionQueryService.findAll(eq("DISPLAY_ORDER"))).thenReturn(List.of(
            new ExhibitionResponse.Summary(1L, "장인이 빚은 공간의 온기", "집 안에 들이고 싶은 공예 4선",
                "https://example.com/banner.webp", 4)));

        mockMvc.perform(get("/api/exhibitions").param("sort", "DISPLAY_ORDER"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "exhibition-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("기획전")
                    .summary("기획전 목록")
                    .description("홈 기획전 섹션과 기획전 목록 화면에 쓰는 노출 중 기획전 목록입니다. 로그인 없이 조회합니다.\n"
                        + "- sort(ENUM, 대소문자·'-' 무관, 모르는 값은 DISPLAY_ORDER):\n" + exhibitionSorts())
                    .queryParameters(
                        parameterWithName("sort").description("정렬 ENUM: DISPLAY_ORDER | NEWEST (상세는 위 설명)").optional())
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].exhibitionId").type(JsonFieldType.NUMBER).description("기획전 ID"),
                        fieldWithPath("data[].title").type(JsonFieldType.STRING).description("기획전 제목"),
                        fieldWithPath("data[].subtitle").type(JsonFieldType.STRING).optional().description("부제"),
                        fieldWithPath("data[].bannerImageUrl").type(JsonFieldType.STRING).optional().description("배너 이미지 URL"),
                        fieldWithPath("data[].productCount").type(JsonFieldType.NUMBER).description("묶인 상품 수")))
                    .build())));
    }

    @Test
    @DisplayName("기획전 상세 조회 — 소개와 판매 중 상품을 기획 순서(또는 지정 정렬)로 조회한다")
    void detail() throws Exception {
        when(exhibitionQueryService.findById(eq(1L), any())).thenReturn(new ExhibitionResponse.Detail(
            1L, "장인이 빚은 공간의 온기", "집 안에 들이고 싶은 공예 4선", "공간에 은은한 분위기를 더하는 장인의 작품을 모았습니다.",
            "https://example.com/banner.webp", "CURATED", List.of(PRODUCT)));

        mockMvc.perform(get("/api/exhibitions/{exhibitionId}", 1L).param("sort", "CURATED"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "exhibition-detail",
                resource(ResourceSnippetParameters.builder()
                    .tag("기획전")
                    .summary("기획전 상세")
                    .description("기획전 소개와 ON_SALE 상품 목록을 조회합니다. 존재하지 않거나 노출 중이 아니면 404 입니다.\n"
                        + "- sort(ENUM, 대소문자·'-' 무관, 모르는 값은 CURATED):\n" + productSorts())
                    .pathParameters(parameterWithName("exhibitionId").description("기획전 ID"))
                    .queryParameters(
                        parameterWithName("sort").description("상품 정렬 ENUM: CURATED | NEWEST | PRICE_ASC | PRICE_DESC (상세는 위 설명)").optional())
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.exhibitionId").type(JsonFieldType.NUMBER).description("기획전 ID"),
                        fieldWithPath("data.title").type(JsonFieldType.STRING).description("기획전 제목"),
                        fieldWithPath("data.subtitle").type(JsonFieldType.STRING).optional().description("부제"),
                        fieldWithPath("data.description").type(JsonFieldType.STRING).optional().description("기획전 소개"),
                        fieldWithPath("data.bannerImageUrl").type(JsonFieldType.STRING).optional().description("배너 이미지 URL"),
                        fieldWithPath("data.sort").type(JsonFieldType.STRING).description("적용된 상품 정렬 ENUM"),
                        fieldWithPath("data.products[].productId").type(JsonFieldType.NUMBER).description("상품 ID"),
                        fieldWithPath("data.products[].artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.products[].categoryId").type(JsonFieldType.NUMBER).optional().description("카테고리 ID"),
                        fieldWithPath("data.products[].categoryName").type(JsonFieldType.STRING).optional().description("카테고리명"),
                        fieldWithPath("data.products[].subcategoryId").type(JsonFieldType.NUMBER).optional().description("서브카테고리 ID"),
                        fieldWithPath("data.products[].subcategoryName").type(JsonFieldType.STRING).optional().description("서브카테고리명"),
                        fieldWithPath("data.products[].title").type(JsonFieldType.STRING).description("상품명"),
                        fieldWithPath("data.products[].description").type(JsonFieldType.STRING).optional().description("상품 설명"),
                        fieldWithPath("data.products[].price").type(JsonFieldType.NUMBER).description("가격"),
                        fieldWithPath("data.products[].stock").type(JsonFieldType.NUMBER).description("재고"),
                        fieldWithPath("data.products[].thumbnailUrl").type(JsonFieldType.STRING).optional().description("대표 이미지 URL"),
                        fieldWithPath("data.products[].status").type(JsonFieldType.STRING).description("상태"),
                        fieldWithPath("data.products[].createdAt").type(JsonFieldType.STRING).description("등록일시"),
                        fieldWithPath("data.products[].updatedAt").type(JsonFieldType.STRING).description("수정일시"),
                        fieldWithPath("data.products[].giftThemes").type(JsonFieldType.ARRAY).description("선물 테마"),
                        fieldWithPath("data.products[].purposeTags").type(JsonFieldType.ARRAY).description("용도 태그"),
                        fieldWithPath("data.products[].productionPeriodDays").type(JsonFieldType.NUMBER).optional().description("제작 기간(일)"),
                        fieldWithPath("data.products[].colors").type(JsonFieldType.ARRAY).description("색상")))
                    .build())));
    }
}
