package com.jangingmall.backend.global.health;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.JsonFieldType;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest extends RestDocsControllerTest {

    @Test
    @DisplayName("헬스체크 엔드포인트가 200과 status ok를 반환한다")
    void healthCheck_returnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("ok"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "health",
                resource(ResourceSnippetParameters.builder()
                    .tag("공통")
                    .summary("헬스체크")
                    .description("서버 상태를 확인합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("서버 상태 (ok)")
                    ))
                    .build()
                )
            ));
    }
}
