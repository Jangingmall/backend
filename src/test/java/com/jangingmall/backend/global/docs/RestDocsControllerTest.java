package com.jangingmall.backend.global.docs;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Controller 슬라이스 테스트 공통 베이스.
 *
 * <p>서브클래스는 다음만 선언한다:
 * <pre>
 * {@literal @}WebMvcTest(XxxController.class)
 * {@literal @}Import(SecurityConfig.class)
 * {@literal @}MockitoBean XxxService xxxService;
 * </pre>
 *
 * <p>에러 응답 필드 디스크립터는 {@link #ERROR_RESPONSE_FIELDS}를 재사용한다.
 * 성공 응답 data.* 필드는 엔드포인트별로 다르므로 각 테스트에서 인라인으로 선언한다.
 */
@ExtendWith(RestDocumentationExtension.class)
@WithMockUser
public abstract class RestDocsControllerTest {

    protected MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUpRestDocs(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .apply(MockMvcRestDocumentation.documentationConfiguration(restDocumentation))
            .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 우선순위 1: ApiErrorResponse 공통 필드 상수
    //
    // 사용 기준: 에러 케이스의 responseFields()가 success/status/errorCode/message
    //           구조를 그대로 따를 때. 케이스별 맥락 설명이 필요하면 인라인으로 선언.
    //
    // 변경 시 이 1곳만 수정하면 전체 에러 문서에 반영된다.
    // ──────────────────────────────────────────────────────────────────────────
    protected static final FieldDescriptor[] ERROR_RESPONSE_FIELDS = {
        fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("false — 항상 실패"),
        fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
        fieldWithPath("errorCode").type(JsonFieldType.STRING).description("ErrorCode 식별자"),
        fieldWithPath("message").type(JsonFieldType.STRING).description("에러 메시지"),
    };

    // ──────────────────────────────────────────────────────────────────────────
    // 우선순위 2: ApiResponse<T> 공통 봉투 필드 (data는 서브클래스에서 추가)
    // ──────────────────────────────────────────────────────────────────────────
    protected static FieldDescriptor[] successEnvelopeFields(FieldDescriptor... dataFields) {
        FieldDescriptor[] envelope = {
            fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("true — 항상 성공"),
            fieldWithPath("status").type(JsonFieldType.NUMBER).description("HTTP 상태 코드"),
        };
        FieldDescriptor[] combined = new FieldDescriptor[envelope.length + dataFields.length];
        System.arraycopy(envelope, 0, combined, 0, envelope.length);
        System.arraycopy(dataFields, 0, combined, envelope.length, dataFields.length);
        return combined;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 우선순위 3: documentError() 헬퍼
    //
    // 사용 기준: 에러 케이스가 ERROR_RESPONSE_FIELDS 구조를 그대로 따르고
    //           추가 검증이나 requestFields가 없을 때.
    //           구조가 달라지면 인라인으로 직접 document()를 호출한다.
    //
    // 장단점:
    //   장점 — 에러 케이스 추가가 andDo(documentError(...)) 1줄로 압축됨
    //   단점 — 파라미터 증가 시 메서드 오버로드 필요, 유연성 제한
    // ──────────────────────────────────────────────────────────────────────────
    protected org.springframework.test.web.servlet.ResultHandler documentError(
        String identifier,
        String tag,
        String summary,
        String description
    ) {
        return MockMvcRestDocumentationWrapper.document(
            identifier,
            resource(ResourceSnippetParameters.builder()
                .tag(tag)
                .summary(summary)
                .description(description)
                .responseFields(ERROR_RESPONSE_FIELDS)
                .build()
            )
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ErrorCode → HttpStatus assertion helper
    // ──────────────────────────────────────────────────────────────────────────
    protected static org.springframework.test.web.servlet.ResultMatcher errorStatus(ErrorCode errorCode) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .status().is(errorCode.httpStatus().value());
    }
}
