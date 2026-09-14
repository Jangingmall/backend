package com.jangingmall.backend.product.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.product.application.CategoryQueryService;
import com.jangingmall.backend.product.application.CategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private CategoryQueryService categoryQueryService;

    @Test
    @DisplayName("카테고리 목록 조회 — 전체 카테고리 목록을 반환한다")
    void categories() throws Exception {
        when(categoryQueryService.findAllCategories()).thenReturn(List.of(
            new CategoryResponse.CategoryItem(1L, "도자기"),
            new CategoryResponse.CategoryItem(2L, "목공예")
        ));

        mockMvc.perform(get("/api/products/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].categoryId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "category-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("카테고리")
                    .summary("카테고리 목록")
                    .description("전체 카테고리 목록을 조회합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                        fieldWithPath("data[].name").type(JsonFieldType.STRING).description("카테고리명")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("메인 카테고리 목록 조회 — 전체 카테고리 목록을 반환한다")
    void mainCategories() throws Exception {
        when(categoryQueryService.findAllCategories()).thenReturn(List.of(
            new CategoryResponse.CategoryItem(1L, "도자기")
        ));

        mockMvc.perform(get("/api/products/categories/main"))
            .andExpect(status().isOk())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "category-main-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("카테고리")
                    .summary("메인 카테고리 목록")
                    .description("메인 화면용 카테고리 목록을 조회합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].categoryId").type(JsonFieldType.NUMBER).description("카테고리 ID"),
                        fieldWithPath("data[].name").type(JsonFieldType.STRING).description("카테고리명")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("서브카테고리 목록 조회 — 전체 서브카테고리 목록을 반환한다")
    void subcategories() throws Exception {
        when(categoryQueryService.findAllSubcategories()).thenReturn(List.of(
            new CategoryResponse.SubcategoryItem(1L, 1L, "다기·찻잔"),
            new CategoryResponse.SubcategoryItem(2L, 1L, "화병·항아리")
        ));

        mockMvc.perform(get("/api/products/subcategories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].subcategoryId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "subcategory-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("카테고리")
                    .summary("서브카테고리 목록")
                    .description("전체 서브카테고리 목록을 조회합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].subcategoryId").type(JsonFieldType.NUMBER).description("서브카테고리 ID"),
                        fieldWithPath("data[].categoryId").type(JsonFieldType.NUMBER).description("상위 카테고리 ID"),
                        fieldWithPath("data[].name").type(JsonFieldType.STRING).description("서브카테고리명")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("소재 목록 조회 — subcategoryId로 필터링된 소재 목록을 반환한다")
    void materials() throws Exception {
        when(categoryQueryService.findMaterials(1L)).thenReturn(
            List.of("백자", "분청", "청자", "옹기토")
        );

        mockMvc.perform(get("/api/products/materials").param("subcategoryId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value("백자"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "material-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("카테고리")
                    .summary("소재 목록")
                    .description("subcategoryId로 필터링된 실제 사용 중인 소재 목록을 조회합니다. subcategoryId 미전달 시 빈 배열 반환.")
                    .queryParameters(
                        parameterWithName("subcategoryId").description("서브카테고리 ID (선택)").optional()
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[]").type(JsonFieldType.ARRAY).description("소재 코드 목록")
                    ))
                    .build()
                )
            ));
    }
}
