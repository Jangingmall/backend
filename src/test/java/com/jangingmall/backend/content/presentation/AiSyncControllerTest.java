package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.content.application.ContentService;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiSyncController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "internal-api.backend-auth-token=test-agent-token")
class AiSyncControllerTest extends RestDocsControllerTest {

    private static final String BEARER_AGENT = "Bearer test-agent-token";

    @MockitoBean
    private ContentService contentService;

    @Test
    @DisplayName("AI 상품 일괄 동기화 — AGENT 토큰으로 호출하면 200과 동기화된 상품 수를 반환한다")
    void bulkSync_returnsCount() throws Exception {
        when(contentService.bulkSyncPublishedProductsToAi()).thenReturn(42);

        mockMvc.perform(post("/internal/ai/products/bulk-sync")
                .header(HttpHeaders.AUTHORIZATION, BEARER_AGENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.synced").value(42))
            .andDo(MockMvcRestDocumentationWrapper.document("ai-bulk-sync",
                resource(ResourceSnippetParameters.builder()
                    .summary("AI 상품 일괄 동기화")
                    .description("ON_SALE 상태인 전체 상품을 AI 서버에 일괄 동기화합니다. AGENT 권한 필요.")
                    .responseFields(
                        fieldWithPath("data.synced").type(JsonFieldType.NUMBER).description("동기화 성공한 상품 수"),
                        fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
                        fieldWithPath("message").type(JsonFieldType.STRING).optional().description("응답 메시지"),
                        fieldWithPath("status").type(JsonFieldType.NUMBER).optional().description("HTTP 상태 코드")
                    )
                    .build())));
    }

    @Test
    @DisplayName("AI 상품 일괄 동기화 인증 — AGENT 토큰 없이 호출하면 401을 반환한다")
    void bulkSync_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/ai/products/bulk-sync"))
            .andExpect(status().isUnauthorized());
    }
}
