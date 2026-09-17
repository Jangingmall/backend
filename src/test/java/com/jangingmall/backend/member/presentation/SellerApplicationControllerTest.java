package com.jangingmall.backend.member.presentation;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.GlobalExceptionHandler;
import com.jangingmall.backend.member.application.CursorPage;
import com.jangingmall.backend.member.application.SellerApplicationData;
import com.jangingmall.backend.member.application.SellerApplicationPipelineResult;
import com.jangingmall.backend.member.application.SellerApplicationService;
import com.jangingmall.backend.member.domain.SellerApplication;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SellerApplicationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SellerApplicationControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private SellerApplicationService applications;

    private static final SellerApplicationData SAMPLE = new SellerApplicationData(
        1L, 10L, "김도공 도예", SellerApplication.Status.PENDING,
        LocalDateTime.of(2026, 1, 1, 0, 0), "전통 도자기를 빚습니다",
        "https://cdn.example.com/license.jpg", null,
        new SellerApplicationData.Pipeline(
            SellerApplication.Stage.PENDING, SellerApplication.Stage.PENDING,
            SellerApplication.Stage.PENDING, SellerApplication.Stage.PENDING),
        null
    );

    @Test
    @DisplayName("판매자 신청 제출은 201과 신청 데이터를 반환한다")
    void apply() throws Exception {
        when(applications.apply(eq(1L), any(), any(), any())).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/member/artisans/applications").with(user())
                .contentType(APPLICATION_JSON)
                .content(json(new SellerApplicationController.ApplicationRequest(
                    "김도공 도예", "전통 도자기를 빚습니다", "https://cdn.example.com/license.jpg"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.applicationId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-apply",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청")
                    .summary("판매자 신청 제출")
                    .description("장인 판매자 신청서를 제출합니다.")
                    .requestFields(
                        fieldWithPath("businessName").type(JsonFieldType.STRING).description("공방명 (최대 100자)"),
                        fieldWithPath("introduction").type(JsonFieldType.STRING).description("소개 (최대 255자)"),
                        fieldWithPath("businessLicenseImageUrl").type(JsonFieldType.STRING).description("사업자 등록증 이미지 URL (https://로 시작)")
                    )
                    .responseFields(wrappedApplicationFields())
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("내 판매자 신청 조회는 신청 데이터를 반환한다")
    void getMyApplication() throws Exception {
        when(applications.mine(1L)).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/member/artisans/applications/me").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applicationId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-mine",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청")
                    .summary("내 판매자 신청 조회")
                    .description("현재 회원의 판매자 신청 현황을 반환합니다.")
                    .responseFields(wrappedApplicationFields())
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("관리자 — 판매자 신청 목록 조회는 커서 페이지로 반환한다")
    void listApplicationsAsAdmin() throws Exception {
        CursorPage<SellerApplicationData> page = new CursorPage<>(List.of(SAMPLE), null, false, 1L);
        when(applications.list(eq(99L), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/seller-applications").with(admin())
                .param("limit", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-admin-list",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청 (관리자)")
                    .summary("판매자 신청 목록 (관리자)")
                    .description("전체 판매자 신청 목록을 커서 페이지네이션으로 반환합니다.")
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.items").type(JsonFieldType.ARRAY).description("신청 목록"),
                        fieldWithPath("data.items[].applicationId").type(JsonFieldType.NUMBER).description("신청 ID"),
                        fieldWithPath("data.items[].memberId").type(JsonFieldType.NUMBER).description("신청 회원 ID"),
                        fieldWithPath("data.items[].businessName").type(JsonFieldType.STRING).description("공방명"),
                        fieldWithPath("data.items[].status").type(JsonFieldType.STRING).description("신청 상태 (PENDING/APPROVED/REJECTED)"),
                        fieldWithPath("data.items[].submittedAt").type(JsonFieldType.STRING).description("신청 일시"),
                        fieldWithPath("data.items[].introduction").type(JsonFieldType.STRING).description("소개"),
                        fieldWithPath("data.items[].businessLicenseImageUrl").type(JsonFieldType.STRING).description("사업자 등록증 이미지 URL"),
                        fieldWithPath("data.items[].rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
                        fieldWithPath("data.items[].pipeline.documentReview").type(JsonFieldType.STRING).description("서류 심사 단계"),
                        fieldWithPath("data.items[].pipeline.craftsmanshipReview").type(JsonFieldType.STRING).description("장인성 심사 단계"),
                        fieldWithPath("data.items[].pipeline.digitalConversion").type(JsonFieldType.STRING).description("디지털 전환 단계"),
                        fieldWithPath("data.items[].pipeline.orderSystemIntegration").type(JsonFieldType.STRING).description("주문 시스템 연동 단계"),
                        fieldWithPath("data.items[].qualificationTier").type(JsonFieldType.STRING).optional().description("자격 등급"),
                        fieldWithPath("data.nextCursor").type(JsonFieldType.STRING).optional().description("다음 페이지 커서"),
                        fieldWithPath("data.hasNext").type(JsonFieldType.BOOLEAN).description("다음 페이지 존재 여부"),
                        fieldWithPath("data.totalCount").type(JsonFieldType.NUMBER).description("전체 신청 수")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("관리자 — 판매자 신청 상세 조회는 신청 데이터를 반환한다")
    void getApplicationDetailAsAdmin() throws Exception {
        when(applications.detail(eq(99L), eq(1L))).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/admin/seller-applications/{applicationId}", 1L).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applicationId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-admin-detail",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청 (관리자)")
                    .summary("판매자 신청 상세 (관리자)")
                    .description("특정 판매자 신청 상세 정보를 반환합니다.")
                    .responseFields(wrappedApplicationFields())
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("관리자 — 판매자 신청 승인은 승인된 데이터를 반환한다")
    void approveApplicationAsAdmin() throws Exception {
        SellerApplicationData approved = new SellerApplicationData(
            1L, 10L, "김도공 도예", SellerApplication.Status.APPROVED,
            LocalDateTime.of(2026, 1, 1, 0, 0), "전통 도자기를 빚습니다",
            "https://cdn.example.com/license.jpg", null,
            new SellerApplicationData.Pipeline(
                SellerApplication.Stage.COMPLETED, SellerApplication.Stage.COMPLETED,
                SellerApplication.Stage.COMPLETED, SellerApplication.Stage.COMPLETED),
            null
        );
        when(applications.approve(eq(99L), eq(1L))).thenReturn(approved);

        mockMvc.perform(post("/api/admin/seller-applications/{applicationId}/approve", 1L).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-approve",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청 (관리자)")
                    .summary("판매자 신청 승인")
                    .description("판매자 신청을 승인합니다. 모든 파이프라인 단계가 COMPLETED로 변경됩니다.")
                    .responseFields(wrappedApplicationFields())
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("관리자 — 판매자 신청 거절은 거절된 데이터를 반환한다")
    void rejectApplicationAsAdmin() throws Exception {
        SellerApplicationData rejected = new SellerApplicationData(
            1L, 10L, "김도공 도예", SellerApplication.Status.REJECTED,
            LocalDateTime.of(2026, 1, 1, 0, 0), "전통 도자기를 빚습니다",
            "https://cdn.example.com/license.jpg", "서류 미비",
            new SellerApplicationData.Pipeline(
                SellerApplication.Stage.FAILED, SellerApplication.Stage.PENDING,
                SellerApplication.Stage.PENDING, SellerApplication.Stage.PENDING),
            null
        );
        when(applications.reject(eq(99L), eq(1L), any())).thenReturn(rejected);

        mockMvc.perform(post("/api/admin/seller-applications/{applicationId}/reject", 1L).with(admin())
                .contentType(APPLICATION_JSON)
                .content(json(new SellerApplicationController.Rejection("서류 미비"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-reject",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청 (관리자)")
                    .summary("판매자 신청 거절")
                    .description("판매자 신청을 거절합니다.")
                    .requestFields(
                        fieldWithPath("reason").type(JsonFieldType.STRING).description("거절 사유")
                    )
                    .responseFields(wrappedApplicationFields())
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("관리자 — 파이프라인 단계 수정은 수정된 파이프라인을 반환한다")
    void updatePipelineAsAdmin() throws Exception {
        SellerApplicationPipelineResult result = new SellerApplicationPipelineResult(
            1L,
            new SellerApplicationData.Pipeline(
                SellerApplication.Stage.COMPLETED, SellerApplication.Stage.IN_PROGRESS,
                SellerApplication.Stage.PENDING, SellerApplication.Stage.PENDING),
            SellerApplication.Status.PENDING,
            null
        );
        when(applications.updatePipeline(eq(99L), eq(1L), any(), any(), any())).thenReturn(result);

        mockMvc.perform(patch("/api/admin/artisans/applications/{applicationId}/pipeline", 1L).with(admin())
                .contentType(APPLICATION_JSON)
                .content(json(new SellerApplicationController.Pipeline(
                    SellerApplication.Step.CRAFTSMANSHIP_REVIEW,
                    SellerApplication.Stage.IN_PROGRESS,
                    null
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.applicationId").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "seller-application-pipeline-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("판매자 신청 (관리자)")
                    .summary("파이프라인 단계 수정")
                    .description("판매자 신청 파이프라인의 특정 단계 상태를 수정합니다.")
                    .requestFields(
                        fieldWithPath("step").type(JsonFieldType.STRING).description("단계 (DOCUMENT_REVIEW/CRAFTSMANSHIP_REVIEW/DIGITAL_CONVERSION/ORDER_SYSTEM_INTEGRATION)"),
                        fieldWithPath("status").type(JsonFieldType.STRING).description("상태 (PENDING/IN_PROGRESS/COMPLETED/FAILED)"),
                        fieldWithPath("qualificationTier").type(JsonFieldType.STRING).optional().description("자격 등급 (선택)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.applicationId").type(JsonFieldType.NUMBER).description("신청 ID"),
                        fieldWithPath("data.pipeline.documentReview").type(JsonFieldType.STRING).description("서류 심사"),
                        fieldWithPath("data.pipeline.craftsmanshipReview").type(JsonFieldType.STRING).description("장인성 심사"),
                        fieldWithPath("data.pipeline.digitalConversion").type(JsonFieldType.STRING).description("디지털 전환"),
                        fieldWithPath("data.pipeline.orderSystemIntegration").type(JsonFieldType.STRING).description("주문 시스템 연동"),
                        fieldWithPath("data.overallStatus").type(JsonFieldType.STRING).description("전체 신청 상태"),
                        fieldWithPath("data.qualificationTier").type(JsonFieldType.STRING).optional().description("자격 등급")
                    ))
                    .build()
                )
            ));
    }

    private FieldDescriptor[] wrappedApplicationFields() {
        return successEnvelopeFields(
            fieldWithPath("data.applicationId").type(JsonFieldType.NUMBER).description("신청 ID"),
            fieldWithPath("data.memberId").type(JsonFieldType.NUMBER).description("신청 회원 ID"),
            fieldWithPath("data.businessName").type(JsonFieldType.STRING).description("공방명"),
            fieldWithPath("data.status").type(JsonFieldType.STRING).description("신청 상태 (PENDING/APPROVED/REJECTED)"),
            fieldWithPath("data.submittedAt").type(JsonFieldType.STRING).description("신청 일시"),
            fieldWithPath("data.introduction").type(JsonFieldType.STRING).description("소개"),
            fieldWithPath("data.businessLicenseImageUrl").type(JsonFieldType.STRING).description("사업자 등록증 이미지 URL"),
            fieldWithPath("data.rejectReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
            fieldWithPath("data.pipeline.documentReview").type(JsonFieldType.STRING).description("서류 심사 단계"),
            fieldWithPath("data.pipeline.craftsmanshipReview").type(JsonFieldType.STRING).description("장인성 심사 단계"),
            fieldWithPath("data.pipeline.digitalConversion").type(JsonFieldType.STRING).description("디지털 전환 단계"),
            fieldWithPath("data.pipeline.orderSystemIntegration").type(JsonFieldType.STRING).description("주문 시스템 연동 단계"),
            fieldWithPath("data.qualificationTier").type(JsonFieldType.STRING).optional().description("자격 등급")
        );
    }

    private RequestPostProcessor user() {
        return authentication(new UsernamePasswordAuthenticationToken(
            1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
            99L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
