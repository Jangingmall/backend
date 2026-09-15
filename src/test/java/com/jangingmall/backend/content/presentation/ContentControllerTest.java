package com.jangingmall.backend.content.presentation;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.jangingmall.backend.content.application.ContentResponse;
import com.jangingmall.backend.content.application.ContentService;
import com.jangingmall.backend.content.domain.BlockTag;
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

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentController.class)
@Import(SecurityConfig.class)
class ContentControllerTest extends RestDocsControllerTest {

    @MockitoBean
    private ContentService contentService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0, 0);

    private static final ContentResponse.BlockView H2_BLOCK =
        new ContentResponse.BlockView(1, "h2", false, null, null, "청자 다완");

    private static final ContentResponse.BlockView P_BLOCK =
        new ContentResponse.BlockView(2, "p", false, null, null, "고려 시대 최고의 청자");

    private static final ContentResponse.Detail SAMPLE_DETAIL =
        new ContentResponse.Detail(1L, 10L, ContentStatus.DRAFT, 1, List.of(H2_BLOCK, P_BLOCK));

    private static final org.springframework.restdocs.payload.FieldDescriptor[] DETAIL_FIELDS = {
        fieldWithPath("data.contentId").type(JsonFieldType.NUMBER).description("콘텐츠 ID"),
        fieldWithPath("data.productId").type(JsonFieldType.NUMBER).description("상품 ID"),
        fieldWithPath("data.status").type(JsonFieldType.STRING).description("콘텐츠 상태 (DRAFT | PENDING_REVIEW | APPROVED | REJECTED | PUBLISHED)"),
        fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("버전 번호"),
        fieldWithPath("data.blocks[].order").type(JsonFieldType.NUMBER).description("표시 순서"),
        fieldWithPath("data.blocks[].tag").type(JsonFieldType.STRING).description("블록 태그 (h2 | p | img | video)"),
        fieldWithPath("data.blocks[].hasImage").type(JsonFieldType.BOOLEAN).description("이미지 포함 여부"),
        fieldWithPath("data.blocks[].imageId").type(JsonFieldType.STRING).optional().description("이미지 ID (tag=img일 때만)"),
        fieldWithPath("data.blocks[].videoUrl").type(JsonFieldType.STRING).optional().description("영상 URL (tag=video일 때만)"),
        fieldWithPath("data.blocks[].text").type(JsonFieldType.STRING).optional().description("텍스트 내용 (h2/p일 때만)"),
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
            .andExpect(jsonPath("$.data.blocks").isArray())
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-get",
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("콘텐츠 조회")
                    .description("AI가 생성한 초안을 블록 리스트로 조회합니다.")
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
    @DisplayName("문단 일괄 수정 — 블록 목록을 교체하고 수정된 콘텐츠를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void bulkUpdateBlocks() throws Exception {
        when(contentService.bulkUpdateBlocks(any())).thenReturn(SAMPLE_DETAIL);

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.BulkUpdate(List.of(
                    new ContentRequest.BlockInput(1, BlockTag.h2, "소제목", null, null),
                    new ContentRequest.BlockInput(2, BlockTag.p, "본문", null, null)
                )))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.version").value(1))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-bulk-update",
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("contentId").description("콘텐츠 ID")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("문단 일괄 수정")
                    .description("여러 블록을 한 번에 교체합니다. 순서 변경·전체 재작성에 사용합니다.")
                    .requestFields(
                        fieldWithPath("blocks[].order").type(JsonFieldType.NUMBER).description("표시 순서"),
                        fieldWithPath("blocks[].tag").type(JsonFieldType.STRING).description("블록 태그 (h2 | p | img | video)"),
                        fieldWithPath("blocks[].text").type(JsonFieldType.STRING).optional().description("텍스트 (h2/p 블록)"),
                        fieldWithPath("blocks[].imageUrl").type(JsonFieldType.STRING).optional().description("이미지 URL (img 블록)"),
                        fieldWithPath("blocks[].videoUrl").type(JsonFieldType.STRING).optional().description("영상 URL (video 블록)")
                    )
                    .responseFields(successEnvelopeFields(DETAIL_FIELDS))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("문단 일괄 수정 — blocks가 비어 있으면 400을 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void bulkUpdateBlocksEmpty() throws Exception {
        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.BulkUpdate(List.of()))))
            .andExpect(status().isBadRequest())
            .andDo(documentError("content-bulk-update-invalid", "콘텐츠", "문단 일괄 수정 — 입력 오류", "blocks가 비어 있으면 400을 반환합니다."));
    }

    @Test
    @DisplayName("단건 블록 수정 — 특정 순서의 블록을 수정하고 버전과 블록 정보를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void updateBlock() throws Exception {
        ContentResponse.BlockEdit blockEdit = new ContentResponse.BlockEdit(1L, 2,
            new ContentResponse.BlockView(1, "p", false, null, null, "수정된 텍스트"));
        when(contentService.updateBlock(any())).thenReturn(blockEdit);

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}/blocks/{blockOrder}", 10L, 1L, 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.BlockUpdate(BlockTag.p, "수정된 텍스트", null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(1))
            .andExpect(jsonPath("$.data.version").value(2))
            .andExpect(jsonPath("$.data.block.text").value("수정된 텍스트"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-block-update",
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("contentId").description("콘텐츠 ID"),
                    parameterWithName("blockOrder").description("수정할 블록 순서")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("단건 블록 수정")
                    .description("특정 순서의 블록 하나만 수정합니다. 텍스트 블록은 text만, 이미지 블록은 imageUrl만 전달합니다.")
                    .requestFields(
                        fieldWithPath("tag").type(JsonFieldType.STRING).optional().description("블록 태그 (h2 | p | img | video)"),
                        fieldWithPath("text").type(JsonFieldType.STRING).optional().description("텍스트 내용 (h2/p 블록)"),
                        fieldWithPath("imageUrl").type(JsonFieldType.STRING).optional().description("이미지 URL (img 블록)")
                    )
                    .responseFields(successEnvelopeFields(
                        fieldWithPath("data.contentId").type(JsonFieldType.NUMBER).description("콘텐츠 ID"),
                        fieldWithPath("data.version").type(JsonFieldType.NUMBER).description("수정 후 버전 번호"),
                        fieldWithPath("data.block.order").type(JsonFieldType.NUMBER).description("표시 순서"),
                        fieldWithPath("data.block.tag").type(JsonFieldType.STRING).description("블록 태그"),
                        fieldWithPath("data.block.hasImage").type(JsonFieldType.BOOLEAN).description("이미지 포함 여부"),
                        fieldWithPath("data.block.imageId").type(JsonFieldType.STRING).optional().description("이미지 ID"),
                        fieldWithPath("data.block.videoUrl").type(JsonFieldType.STRING).optional().description("영상 URL"),
                        fieldWithPath("data.block.text").type(JsonFieldType.STRING).optional().description("텍스트 내용")
                    ))
                    .build()
                )
            ));
    }

    @Test
    @DisplayName("단건 블록 수정 — 존재하지 않는 블록 수정 시 404를 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void updateBlockNotFound() throws Exception {
        when(contentService.updateBlock(any())).thenThrow(new NotFoundException("블록을 찾을 수 없습니다"));

        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}/blocks/{blockOrder}", 10L, 1L, 99)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.BlockUpdate(null, "텍스트", null))))
            .andExpect(status().isNotFound())
            .andDo(documentError("content-block-update-not-found", "콘텐츠", "단건 블록 수정 — 블록 없음", "존재하지 않는 blockOrder입니다."));
    }

    @Test
    @DisplayName("버전 이력 조회 — 콘텐츠 편집 이력을 버전 오름차순으로 반환한다")
    @WithMockUser(roles = "ARTISAN")
    void getVersionHistory() throws Exception {
        List<ContentResponse.VersionHistory> history = List.of(
            new ContentResponse.VersionHistory(1, NOW, EditedByType.AI),
            new ContentResponse.VersionHistory(2, NOW.plusMinutes(5), EditedByType.ARTISAN)
        );
        when(contentService.getVersionHistory(anyLong(), any())).thenReturn(history);

        mockMvc.perform(get("/api/content/products/{productId}/contents/versions", 10L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].editedBy").value("AI"))
            .andExpect(jsonPath("$.data[1].editedBy").value("ARTISAN"))
            .andDo(MockMvcRestDocumentationWrapper.document(
                "content-version-history",
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("버전 이력 조회")
                    .description("콘텐츠 편집 이력을 버전 오름차순으로 조회합니다.")
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
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("contentId").description("콘텐츠 ID")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("검토 요청")
                    .description("DRAFT 또는 REJECTED 상태의 콘텐츠를 검토 요청(PENDING_REVIEW)으로 전이합니다.")
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
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("contentId").description("콘텐츠 ID")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("콘텐츠 승인")
                    .description("PENDING_REVIEW 콘텐츠를 APPROVED로 전이하고 사실 확인·사진 일치 체크리스트를 반영합니다.")
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
                pathParameters(
                    parameterWithName("productId").description("상품 ID"),
                    parameterWithName("contentId").description("콘텐츠 ID")
                ),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("콘텐츠 반려")
                    .description("PENDING_REVIEW 콘텐츠를 REJECTED로 전이합니다. 장인은 수정 후 재요청할 수 있습니다.")
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
                pathParameters(parameterWithName("productId").description("상품 ID")),
                resource(ResourceSnippetParameters.builder()
                    .tag("콘텐츠")
                    .summary("상품 게시")
                    .description("APPROVED 콘텐츠를 PUBLISHED 상태로 전이합니다. 이후 상품이 고객에게 노출됩니다.")
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

    @Test
    @DisplayName("콘텐츠 조회 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void getContentUnauthorized() throws Exception {
        mockMvc.perform(get("/api/content/products/{productId}", 10L))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("content-get-unauthorized", "콘텐츠", "콘텐츠 조회 — 인증 없음",
                "인증 없이 콘텐츠를 조회하면 401을 반환합니다."));
    }

    @Test
    @DisplayName("문단 일괄 수정 — 미인증 요청은 401을 반환한다")
    @WithAnonymousUser
    void bulkUpdateUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/content/products/{productId}/contents/{contentId}", 10L, 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ContentRequest.BulkUpdate(List.of()))))
            .andExpect(status().isUnauthorized())
            .andDo(documentError("content-bulk-update-unauthorized", "콘텐츠", "문단 일괄 수정 — 인증 없음",
                "인증 없이 문단을 수정하면 401을 반환합니다."));
    }
}
