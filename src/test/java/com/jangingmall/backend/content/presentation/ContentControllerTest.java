package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import com.jangingmall.backend.content.application.ContentResponse;
import com.jangingmall.backend.content.application.ContentService;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.global.config.SecurityConfig;
import com.jangingmall.backend.global.docs.RestDocsControllerTest;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentController.class)
@Import(SecurityConfig.class)
class ContentControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ContentService contentService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0, 0);

    private static final String SAMPLE_REACT_DOCUMENT =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":["
        + "{\"id\":\"node-001\",\"type\":\"element\",\"tag\":\"h2\","
        + "\"props\":{\"style\":{\"color\":\"#1a1a1a\",\"fontSize\":24,\"fontWeight\":700}},"
        + "\"children\":[{\"id\":\"text-001\",\"type\":\"text\",\"value\":\"60년 전통 이천 청자 다완\",\"marks\":[]}]},"
        + "{\"id\":\"node-002\",\"type\":\"element\",\"tag\":\"p\","
        + "\"props\":{\"style\":{\"color\":\"#444444\",\"lineHeight\":1.8}},"
        + "\"children\":[{\"id\":\"text-002\",\"type\":\"text\",\"value\":\"3대째 이천에서 청자를 빚어온 장인이 직접 제작한 작품입니다.\",\"marks\":[]}]},"
        + "{\"id\":\"node-003\",\"type\":\"element\",\"tag\":\"img\","
        + "\"props\":{\"imageId\":\"hero\",\"alt\":\"이천 청자 다완\",\"style\":{\"objectFit\":\"cover\",\"borderRadius\":8}},"
        + "\"children\":[]},"
        + "{\"id\":\"node-004\",\"type\":\"element\",\"tag\":\"p\","
        + "\"props\":{\"style\":{\"color\":\"#444444\",\"lineHeight\":1.8}},"
        + "\"children\":["
        + "{\"id\":\"text-004a\",\"type\":\"text\",\"value\":\"유약은 \",\"marks\":[]},"
        + "{\"id\":\"text-004b\",\"type\":\"text\",\"value\":\"천연 재료\",\"marks\":[{\"type\":\"bold\"}]},"
        + "{\"id\":\"text-004c\",\"type\":\"text\",\"value\":\"만 사용합니다.\",\"marks\":[]}"
        + "]},"
        + "{\"id\":\"node-005\",\"type\":\"element\",\"tag\":\"table\","
        + "\"props\":{},"
        + "\"children\":["
        + "{\"id\":\"node-005-thead\",\"type\":\"element\",\"tag\":\"thead\",\"props\":{},"
        + "\"children\":[{\"id\":\"node-005-tr0\",\"type\":\"element\",\"tag\":\"tr\",\"props\":{},"
        + "\"children\":["
        + "{\"id\":\"node-005-th0\",\"type\":\"element\",\"tag\":\"th\",\"props\":{},"
        + "\"children\":[{\"id\":\"text-th0\",\"type\":\"text\",\"value\":\"항목\",\"marks\":[]}]},"
        + "{\"id\":\"node-005-th1\",\"type\":\"element\",\"tag\":\"th\",\"props\":{},"
        + "\"children\":[{\"id\":\"text-th1\",\"type\":\"text\",\"value\":\"내용\",\"marks\":[]}]}"
        + "]}]},"
        + "{\"id\":\"node-005-tbody\",\"type\":\"element\",\"tag\":\"tbody\",\"props\":{},"
        + "\"children\":[{\"id\":\"node-005-tr1\",\"type\":\"element\",\"tag\":\"tr\",\"props\":{},"
        + "\"children\":["
        + "{\"id\":\"node-005-td0\",\"type\":\"element\",\"tag\":\"td\",\"props\":{},"
        + "\"children\":[{\"id\":\"text-td0\",\"type\":\"text\",\"value\":\"소재\",\"marks\":[]}]},"
        + "{\"id\":\"node-005-td1\",\"type\":\"element\",\"tag\":\"td\",\"props\":{},"
        + "\"children\":[{\"id\":\"text-td1\",\"type\":\"text\",\"value\":\"고령토, 천연유약\",\"marks\":[]}]}"
        + "]}]}"
        + "]}"
        + "]}";

    private static final ContentResponse.Detail SAMPLE_DETAIL =
        new ContentResponse.Detail(1L, 10L, ContentStatus.DRAFT, 1, SAMPLE_REACT_DOCUMENT);

    private static final org.springframework.restdocs.payload.FieldDescriptor[] DETAIL_FIELDS = {
        fieldWithPath("data.contentId").type(JsonFieldType.NUMBER).description("콘텐츠 ID"),
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("콘텐츠 상태"),
        fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("버전 번호"),
        subsectionWithPath("data.reactDocument").type(JsonFieldType.OBJECT).optional().description("AI가 생성한 react_document AST. schemaVersion, canvasWidth, root[] 구조"),
    };

    @Test
    @DisplayName("콘텐츠 조회 — 장인이 AI 생성 초안을 조회한다")
    @WithMockUser(roles = "ARTISAN")
    void getContent() throws Exception {
        when(contentService.getContent(anyLong(), any())).thenReturn(SAMPLE_DETAIL);

        mockMvc.perform(get("/api/content/products/{productId}/contents", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(1))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.reactDocument").exists())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-get",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("콘텐츠 조회")
                    .description("AI가 생성한 react_document를 조회합니다. reactDocument 필드는 AI 완료 전까지 null입니다.\n\n"
                        + enumTables(
                            enumTable("ContentStatus", entries(
                                "DRAFT", "AI 생성 초안",
                                "PENDING_REVIEW", "관리자 검토 요청",
                                "APPROVED", "승인됨",
                                "REJECTED", "반려됨",
                                "PUBLISHED", "게시됨"
                            )),
                            enumTable("EditedByType", entries("AI", "AI 생성", "ARTISAN", "장인 편집"))
                        ))
                    .pathParameters(parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(DETAIL_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("콘텐츠 조회 — 소유자가 아니면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void getContentForbidden() throws Exception {
        when(contentService.getContent(anyLong(), any())).thenThrow(new ForbiddenException());

        mockMvc.perform(get("/api/content/products/{productId}/contents", 10L))
            .andExpect(status().isForbidden())
            .andDo(documentError("content-get-forbidden", "콘텐츠", "콘텐츠 조회 — 권한 없음", "소유자가 아닌 경우 403을 반환합니다."));
    }

    @Test
    @DisplayName("콘텐츠 조회 — 콘텐츠가 없으면 404를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void getContentNotFound() throws Exception {
        when(contentService.getContent(anyLong(), any())).thenThrow(new NotFoundException("콘텐츠를 찾을 수 없습니다"));

        mockMvc.perform(get("/api/content/products/{productId}/contents", 10L))
            .andExpect(status().isNotFound())
            .andDo(documentError("content-get-not-found", "콘텐츠", "콘텐츠 조회 — 없음", "AI 생성이 완료되지 않은 경우입니다."));
    }

    @Test
    @DisplayName("콘텐츠 조회 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void getContentUnauthorized() throws Exception {
        mockMvc.perform(get("/api/content/products/{productId}/contents", 10L))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("content-get-unauthorized", "콘텐츠", "콘텐츠 조회 — 인증 없음",
                "인증 없이 콘텐츠를 조회하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("버전 이력 조회 — 콘텐츠 편집 이력을 버전 오름차순으로 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void getVersionHistory() throws Exception {
        List<ContentResponse.VersionHistory> history = List.of(
            new ContentResponse.VersionHistory(1, NOW, EditedByType.AI),
            new ContentResponse.VersionHistory(2, NOW.plusMinutes(5), EditedByType.AI)
        );
        when(contentService.getVersionHistory(anyLong(), any())).thenReturn(history);

        mockMvc.perform(get("/api/content/products/{productId}/contents/versions", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].editedBy").value("AI"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-version-history",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("버전 이력 조회")
                    .description("콘텐츠 편집 이력을 버전 오름차순으로 조회합니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data[].version").type(JsonFieldType.NUMBER).description("버전 번호"),
                        fieldWithPath("data[].editedAt").type(JsonFieldType.STRING).description("수정 시각"),
                        fieldWithPath("data[].editedBy").type(JsonFieldType.STRING).description("수정자 유형 (AI | ARTISAN)")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("버전 이력 조회 — 소유자가 아니면 403을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void getVersionHistoryForbidden() throws Exception {
        when(contentService.getVersionHistory(anyLong(), any())).thenThrow(new ForbiddenException());

        mockMvc.perform(get("/api/content/products/{productId}/contents/versions", 10L))
            .andExpect(status().isForbidden())
            .andDo(documentError("content-version-history-forbidden", "콘텐츠", "버전 이력 조회 — 권한 없음", "소유자가 아닌 경우 403을 반환합니다."));
    }

    private static final ContentResponse.StatusChanged STATUS_CHANGED_PENDING =
        new ContentResponse.StatusChanged(1L, ContentStatus.PENDING_REVIEW);
    private static final ContentResponse.StatusChanged STATUS_CHANGED_APPROVED =
        new ContentResponse.StatusChanged(1L, ContentStatus.APPROVED);
    private static final ContentResponse.StatusChanged STATUS_CHANGED_REJECTED =
        new ContentResponse.StatusChanged(1L, ContentStatus.REJECTED);
    private static final ContentResponse.StatusChanged STATUS_CHANGED_PUBLISHED =
        new ContentResponse.StatusChanged(1L, ContentStatus.PUBLISHED);

    private static final org.springframework.restdocs.payload.FieldDescriptor[] STATUS_CHANGED_FIELDS = {
        fieldWithPath("data.contentId").type(JsonFieldType.NUMBER).description("콘텐츠 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("변경된 콘텐츠 상태"),
    };

    @Test
    @DisplayName("검토 요청 — DRAFT 콘텐츠를 PENDING_REVIEW로 전이한다")
    @WithMockUser(roles = "ARTISAN")
    void submitForReview() throws Exception {
        when(contentService.submitForReview(any())).thenReturn(STATUS_CHANGED_PENDING);

        mockMvc.perform(post("/api/content/products/{productId}/contents/{contentId}/submit", 10L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-submit",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("검토 요청")
                    .description("DRAFT 또는 REJECTED 상태의 콘텐츠를 검토 요청(PENDING_REVIEW)으로 전이합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("contentId").description("콘텐츠 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(STATUS_CHANGED_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("콘텐츠 승인 — PENDING_REVIEW 콘텐츠를 APPROVED로 전이한다")
    @WithMockUser(roles = "ARTISAN")
    void approve() throws Exception {
        when(contentService.approve(any())).thenReturn(STATUS_CHANGED_APPROVED);

        mockMvc.perform(post("/api/content/products/{productId}/contents/{contentId}/approve", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.Approve(true, true, false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-approve",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("콘텐츠 승인")
                    .description("PENDING_REVIEW 콘텐츠를 APPROVED로 전이하고 사실 확인·사진 일치 체크리스트를 반영합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("contentId").description("콘텐츠 ID").type(SimpleType.INTEGER)
                    )
                    .requestFields(
                        fieldWithPath("factCheckConfirmed").type(JsonFieldType.BOOLEAN).description("사실 확인 완료 여부"),
                        fieldWithPath("photoMatchConfirmed").type(JsonFieldType.BOOLEAN).description("사진 일치 확인 여부"),
                        fieldWithPath("displayApprovalBadge").type(JsonFieldType.BOOLEAN).description("승인 배지 노출 여부")
                    )
                    .responseFields(successEnvelopeFields(STATUS_CHANGED_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("콘텐츠 승인 — PENDING_REVIEW가 아니면 422를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void approveInvalidTransition() throws Exception {
        when(contentService.approve(any())).thenThrow(new BusinessRuleViolationException("현재 상태에서 허용되지 않는 콘텐츠 상태 전이입니다"));

        mockMvc.perform(post("/api/content/products/{productId}/contents/{contentId}/approve", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.Approve(true, true, false))))
            .andExpect(status().isUnprocessableEntity())
            .andDo(documentError("content-approve-invalid", "콘텐츠", "콘텐츠 승인 — 상태 오류", "PENDING_REVIEW가 아닌 경우 422를 반환합니다."));
    }

    @Test
    @DisplayName("콘텐츠 반려 — PENDING_REVIEW 콘텐츠를 REJECTED로 전이한다")
    @WithMockUser(roles = "ARTISAN")
    void reject() throws Exception {
        when(contentService.reject(any())).thenReturn(STATUS_CHANGED_REJECTED);

        mockMvc.perform(post("/api/content/products/{productId}/contents/{contentId}/reject", 10L, 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-reject",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("콘텐츠 반려")
                    .description("PENDING_REVIEW 콘텐츠를 REJECTED로 전이합니다. 장인은 수정 후 재요청할 수 있습니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("contentId").description("콘텐츠 ID").type(SimpleType.INTEGER)
                    )
                    .responseFields(successEnvelopeFields(STATUS_CHANGED_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("콘텐츠 반려 — PENDING_REVIEW가 아니면 422를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void rejectInvalidTransition() throws Exception {
        when(contentService.reject(any())).thenThrow(new BusinessRuleViolationException("현재 상태에서 허용되지 않는 콘텐츠 상태 전이입니다"));

        mockMvc.perform(post("/api/content/products/{productId}/contents/{contentId}/reject", 10L, 1L))
            .andExpect(status().isUnprocessableEntity())
            .andDo(documentError("content-reject-invalid", "콘텐츠", "콘텐츠 반려 — 상태 오류", "PENDING_REVIEW가 아닌 경우 422를 반환합니다."));
    }

    @Test
    @DisplayName("게시 — APPROVED 콘텐츠를 PUBLISHED로 전이한다")
    @WithMockUser(roles = "ARTISAN")
    void publish() throws Exception {
        when(contentService.publish(any())).thenReturn(STATUS_CHANGED_PUBLISHED);

        mockMvc.perform(post("/api/content/products/{productId}/publish", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-publish",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("상품 게시")
                    .description("APPROVED 콘텐츠를 PUBLISHED 상태로 전이합니다. 이후 상품이 고객에게 노출됩니다.")
                    .pathParameters(parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER))
                    .responseFields(successEnvelopeFields(STATUS_CHANGED_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("게시 — APPROVED가 아니면 422를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void publishInvalidTransition() throws Exception {
        when(contentService.publish(any())).thenThrow(new BusinessRuleViolationException("콘텐츠가 승인(APPROVED) 상태여야 게시할 수 있습니다"));

        mockMvc.perform(post("/api/content/products/{productId}/publish", 10L))
            .andExpect(status().isUnprocessableEntity())
            .andDo(documentError("content-publish-invalid", "콘텐츠", "게시 — 상태 오류", "APPROVED가 아닌 경우 422를 반환합니다."));
    }

    private static final ContentResponse.BulkUpdated BULK_UPDATED =
        new ContentResponse.BulkUpdated(1L, 10L, ContentStatus.DRAFT, 2);

    private static final org.springframework.restdocs.payload.FieldDescriptor[] BULK_UPDATED_FIELDS = {
        fieldWithPath("data.contentId").type(JsonFieldType.NUMBER).description("콘텐츠 ID"),
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("콘텐츠 상태"),
        fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("낙관적 락 버전"),
    };

    @Test
    @DisplayName("문단 일괄 수정 — nodeId 기준으로 텍스트/imageId를 교체한다")
    @WithMockUser(roles = "ARTISAN")
    void bulkUpdate() throws Exception {
        when(contentService.bulkUpdate(any())).thenReturn(BULK_UPDATED);

        ContentRequest.BulkUpdate request = new ContentRequest.BulkUpdate(List.of(
            new ContentRequest.BulkUpdate.NodePatchItem("node-001", "변경된 제목", null),
            new ContentRequest.BulkUpdate.NodePatchItem("node-002", null, "new-img-id")
        ));

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(1))
            .andExpect(jsonPath("$.data.version").value(2))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-bulk-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("문단 일괄 수정")
                    .description("AI 생성 document에서 nodeId 기준으로 텍스트/imageId를 교체합니다. props.style/layout은 보존됩니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("contentId").description("콘텐츠 ID").type(SimpleType.INTEGER)
                    )
                    .requestFields(
                        fieldWithPath("patches[].nodeId").type(JsonFieldType.STRING).description("수정할 노드 ID"),
                        fieldWithPath("patches[].text").type(JsonFieldType.STRING).optional().description("교체할 텍스트 (null = 유지)"),
                        fieldWithPath("patches[].imageId").type(JsonFieldType.STRING).optional().description("교체할 이미지 ID (null = 유지)")
                    )
                    .responseFields(successEnvelopeFields(BULK_UPDATED_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("문단 일괄 수정 — DRAFT/REJECTED가 아니면 422를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void bulkUpdateInvalidStatus() throws Exception {
        when(contentService.bulkUpdate(any())).thenThrow(new BusinessRuleViolationException("DRAFT 또는 REJECTED 상태에서만 편집할 수 있습니다"));

        ContentRequest.BulkUpdate request = new ContentRequest.BulkUpdate(List.of(
            new ContentRequest.BulkUpdate.NodePatchItem("node-001", "text", null)
        ));

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isUnprocessableEntity())
            .andDo(documentError("content-bulk-update-invalid-status", "콘텐츠", "문단 일괄 수정 — 상태 오류", "DRAFT 또는 REJECTED가 아닌 경우 422를 반환합니다."));
    }

    private static final ContentResponse.BlockUpdated BLOCK_UPDATED =
        new ContentResponse.BlockUpdated(1L, 3, "node-001");

    private static final org.springframework.restdocs.payload.FieldDescriptor[] BLOCK_UPDATED_FIELDS = {
        fieldWithPath("data.contentId").type(JsonFieldType.NUMBER).description("콘텐츠 ID"),
        fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("낙관적 락 버전"),
        fieldWithPath("data.nodeId").type(JsonFieldType.STRING).description("수정된 노드 ID"),
    };

    @Test
    @DisplayName("단건 블록 수정 — nodeId로 특정 노드를 수정한다")
    @WithMockUser(roles = "ARTISAN")
    void updateBlock() throws Exception {
        when(contentService.updateBlock(any())).thenReturn(BLOCK_UPDATED);

        ContentRequest.BlockUpdate request = new ContentRequest.BlockUpdate("수정된 소제목", null);

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}/blocks/{nodeId}",
                    10L, 1L, "node-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(1))
            .andExpect(jsonPath("$.data.nodeId").value("node-001"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-block-update",
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("단건 블록 수정")
                    .description("nodeId로 특정 노드의 텍스트 또는 imageId를 수정합니다.")
                    .pathParameters(
                        parameterWithName("productId").description("상품 ID").type(SimpleType.INTEGER),
                        parameterWithName("contentId").description("콘텐츠 ID").type(SimpleType.INTEGER),
                        parameterWithName("nodeId").description("수정할 노드 ID").type(SimpleType.STRING)
                    )
                    .requestFields(
                        fieldWithPath("text").type(JsonFieldType.STRING).optional().description("교체할 텍스트 (null = 유지)"),
                        fieldWithPath("imageId").type(JsonFieldType.STRING).optional().description("교체할 이미지 ID (null = 유지)")
                    )
                    .responseFields(successEnvelopeFields(BLOCK_UPDATED_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("단건 블록 수정 — 존재하지 않는 nodeId면 404를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void updateBlockNotFound() throws Exception {
        when(contentService.updateBlock(any())).thenThrow(new NotFoundException("해당 순서의 블록을 찾을 수 없습니다"));

        ContentRequest.BlockUpdate request = new ContentRequest.BlockUpdate("텍스트", null);

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}/blocks/{nodeId}",
                    10L, 1L, "non-existent-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)))
            .andExpect(status().isNotFound())
            .andDo(documentError("content-block-update-not-found", "콘텐츠", "단건 블록 수정 — 블록 없음", "존재하지 않는 nodeId인 경우 404를 반환합니다."));
    }
}
