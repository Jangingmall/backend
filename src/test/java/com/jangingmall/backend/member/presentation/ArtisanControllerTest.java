package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.ArtisanService;
import com.jangingmall.backend.member.application.CursorPage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ArtisanController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ArtisanControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ArtisanService artisans;

    private static final Map<String, Object> ARTISAN_ITEM = Map.of(
        "artisanId", 10L,
        "businessName", "김도공 도예",
        "category", "도예",
        "region", "경기도 이천",
        "careerYears", 15,
        "introduction", "전통 도자기를 빚는 장인입니다."
    );

    @Test
    @DisplayName("장인 목록 조회는 커서 페이지로 반환한다")
    void listArtisans() throws Exception {
        CursorPage<Map<String, Object>> page = new CursorPage<>(List.of(ARTISAN_ITEM), null, false, 1L);
        when(artisans.list(any(), any(Integer.class), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/member/artisans")
                .param("limit", "20")
                .param("sort", "POPULAR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "artisan-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인")
                    .summary("장인 목록 조회")
                    .description("장인 목록을 커서 페이지네이션으로 반환합니다. sort 값: POPULAR, MOST_PRODUCTS, RECENTLY_JOINED")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("장인 목록"),
                        fieldWithPath("data.items[].artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.items[].businessName").type(JsonFieldType.STRING).description("공방명"),
                        fieldWithPath("data.items[].category").type(JsonFieldType.STRING).description("카테고리"),
                        fieldWithPath("data.items[].region").type(JsonFieldType.STRING).description("지역"),
                        fieldWithPath("data.items[].careerYears").type(JsonFieldType.NUMBER).description("경력 연수"),
                        fieldWithPath("data.items[].introduction").type(JsonFieldType.STRING).description("소개"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 장인 수")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("장인 상세 조회는 장인 정보를 반환한다")
    void getArtisanDetail() throws Exception {
        when(artisans.detail(10L)).thenReturn(ARTISAN_ITEM);

        mockMvc.perform(get("/api/member/artisans/{artisanId}", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.artisanId").value(10))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "artisan-detail",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인")
                    .summary("장인 상세 조회")
                    .description("장인 ID로 장인 상세 정보를 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.businessName").type(JsonFieldType.STRING).description("공방명"),
                        fieldWithPath("data.category").type(JsonFieldType.STRING).description("카테고리"),
                        fieldWithPath("data.region").type(JsonFieldType.STRING).description("지역"),
                        fieldWithPath("data.careerYears").type(JsonFieldType.NUMBER).description("경력 연수"),
                        fieldWithPath("data.introduction").type(JsonFieldType.STRING).description("소개")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("내 장인 프로필 조회는 본인의 장인 정보를 반환한다")
    void getMyArtisanProfile() throws Exception {
        when(artisans.mine(1L)).thenReturn(ARTISAN_ITEM);

        mockMvc.perform(get("/api/member/artisans/me").with(artisanUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.artisanId").value(10))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "artisan-mine",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인")
                    .summary("내 장인 프로필 조회")
                    .description("인증된 장인의 프로필 정보를 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.businessName").type(JsonFieldType.STRING).description("공방명"),
                        fieldWithPath("data.category").type(JsonFieldType.STRING).description("카테고리"),
                        fieldWithPath("data.region").type(JsonFieldType.STRING).description("지역"),
                        fieldWithPath("data.careerYears").type(JsonFieldType.NUMBER).description("경력 연수"),
                        fieldWithPath("data.introduction").type(JsonFieldType.STRING).description("소개")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("장인 프로필 수정은 수정된 프로필을 반환한다")
    void updateArtisanProfile() throws Exception {
        Map<String, Object> updated = Map.of(
            "artisanId", 10L,
            "businessName", "새공방명",
            "category", "도예",
            "region", "경기도 이천",
            "careerYears", 15,
            "introduction", "새로운 소개입니다."
        );
        when(artisans.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/member/artisans/me").with(artisanUser())
                .contentType(APPLICATION_JSON)
                .content(json(new ArtisanController.Profile(
                    "새공방명", "새로운 소개입니다.", null, null, null, null, null, null, null, null, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.businessName").value("새공방명"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "artisan-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("장인")
                    .summary("장인 프로필 수정")
                    .description("장인 프로필 정보를 부분 수정합니다. null 필드는 변경하지 않습니다.")
                    .requestFields(
                        fieldWithPath("businessName").type(JsonFieldType.STRING).optional().description("공방명"),
                        fieldWithPath("introduction").type(JsonFieldType.STRING).optional().description("소개"),
                        fieldWithPath("profileImageUrl").type(JsonFieldType.STRING).optional().description("프로필 이미지 URL (https://로 시작)"),
                        fieldWithPath("category").type(JsonFieldType.STRING).optional().description("카테고리"),
                        fieldWithPath("region").type(JsonFieldType.STRING).optional().description("지역"),
                        fieldWithPath("careerYears").type(JsonFieldType.NUMBER).optional().description("경력 연수 (0~200)"),
                        fieldWithPath("certifiedYear").type(JsonFieldType.NUMBER).optional().description("인증 연도"),
                        fieldWithPath("lineage").type(JsonFieldType.STRING).optional().description("전통 계보"),
                        fieldWithPath("quote").type(JsonFieldType.STRING).optional().description("좌우명"),
                        fieldWithPath("bio").type(JsonFieldType.STRING).optional().description("상세 소개"),
                        fieldWithPath("videoUrl").type(JsonFieldType.STRING).optional().description("영상 URL (https://로 시작)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.artisanId").type(JsonFieldType.NUMBER).description("장인 ID"),
                        fieldWithPath("data.businessName").type(JsonFieldType.STRING).description("공방명"),
                        fieldWithPath("data.category").type(JsonFieldType.STRING).description("카테고리"),
                        fieldWithPath("data.region").type(JsonFieldType.STRING).description("지역"),
                        fieldWithPath("data.careerYears").type(JsonFieldType.NUMBER).description("경력 연수"),
                        fieldWithPath("data.introduction").type(JsonFieldType.STRING).description("소개")
                    ))
                    .build()
                )
            ));
    }

    private RequestPostProcessor artisanUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_ARTISAN"))));
    }
}
