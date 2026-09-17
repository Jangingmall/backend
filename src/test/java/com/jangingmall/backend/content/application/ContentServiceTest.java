package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.BlockTag;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.ArtisanProfileRepository;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ContentEditHistoryRepository historyRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AiContentClient aiContentClient;
    @Mock
    private ArtisanProfileRepository artisanProfileRepository;
    @Mock
    private InterviewRepository interviewRepository;

    @Captor
    private ArgumentCaptor<Content> contentCaptor;
    @Captor
    private ArgumentCaptor<ContentEditHistory> historyCaptor;

    private ContentService contentService;
    private Product artisanProduct;
    private Content sampleContent;

    private static final String REACT_DOCUMENT_JSON =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}";

    @BeforeEach
    void setUp() {
        contentService = new ContentService(contentRepository, historyRepository, productRepository, aiContentClient, artisanProfileRepository, interviewRepository, new ObjectMapper());
        artisanProduct = Product.create(1L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(artisanProduct, "id", 10L);
        sampleContent = Content.create(10L);
        ReflectionTestUtils.setField(sampleContent, "id", 1L);
        ReflectionTestUtils.setField(sampleContent, "version", 0);
        ReflectionTestUtils.setField(sampleContent, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(sampleContent, "updatedAt", LocalDateTime.now());
    }

    @Test
    @DisplayName("콘텐츠 조회 — 상품 소유자가 조회하면 Detail을 반환한다")
    void getContent() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));

        ContentResponse.Detail detail = contentService.getContent(10L, 1L);

        assertThat(detail.contentId()).isEqualTo(1L);
        assertThat(detail.productId()).isEqualTo(10L);
        assertThat(detail.status()).isEqualTo(ContentStatus.DRAFT);
    }

    @Test
    @DisplayName("콘텐츠 조회 — 소유자가 아니면 ForbiddenException이 발생한다")
    void getContentForbidden() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        assertThatThrownBy(() -> contentService.getContent(10L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("콘텐츠 조회 — 콘텐츠가 없으면 NotFoundException이 발생한다")
    void getContentNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.getContent(10L, 1L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("react_document 저장 — 신규 콘텐츠 생성 후 blob을 저장하고 AI 이력을 기록한다")
    void storeReactDocumentNew() {
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentCommand.StoreReactDocument command = new ContentCommand.StoreReactDocument(10L, REACT_DOCUMENT_JSON, null);
        Content result = contentService.storeReactDocument(command);

        assertThat(result.getProductId()).isEqualTo(10L);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.AI);
    }

    @Test
    @DisplayName("react_document 저장 — 기존 콘텐츠가 있으면 덮어쓴다")
    void storeReactDocumentUpdate() {
        sampleContent.storeReactDocument("{\"schemaVersion\":\"1.0\"}");
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentCommand.StoreReactDocument command = new ContentCommand.StoreReactDocument(10L, REACT_DOCUMENT_JSON, null);
        contentService.storeReactDocument(command);

        verify(contentRepository).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue().getReactDocument()).isEqualTo(REACT_DOCUMENT_JSON);
    }

    @Test
    @DisplayName("버전 이력 조회 — 이력을 오름차순으로 반환한다")
    void getVersionHistory() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        ContentEditHistory h1 = ContentEditHistory.record(1L, 1, EditedByType.AI, null);
        ContentEditHistory h2 = ContentEditHistory.record(1L, 2, EditedByType.AI, null);
        when(historyRepository.findAllByContentIdOrderByVersionAsc(1L)).thenReturn(List.of(h1, h2));

        List<ContentResponse.VersionHistory> history = contentService.getVersionHistory(10L, 1L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).editedBy()).isEqualTo(EditedByType.AI);
    }

    @Test
    @DisplayName("검토 요청 — DRAFT 콘텐츠를 PENDING_REVIEW로 전이한다")
    void submitForReview() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);

        ContentCommand.SubmitForReview command = new ContentCommand.SubmitForReview(10L, 1L, 1L);
        ContentResponse.StatusChanged result = contentService.submitForReview(command);

        assertThat(result.status()).isEqualTo(ContentStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("검토 요청 — 이미 PENDING_REVIEW이면 BusinessRuleViolationException이 발생한다")
    void submitForReviewInvalidTransition() {
        sampleContent.submitForReview();
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));

        ContentCommand.SubmitForReview command = new ContentCommand.SubmitForReview(10L, 1L, 1L);

        assertThatThrownBy(() -> contentService.submitForReview(command))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("콘텐츠 승인 — PENDING_REVIEW 콘텐츠를 APPROVED로 전이하고 체크리스트를 반영한다")
    void approve() {
        sampleContent.submitForReview();
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);

        ContentCommand.Approve command = new ContentCommand.Approve(10L, 1L, 1L, true, true, false);
        ContentResponse.StatusChanged result = contentService.approve(command);

        assertThat(result.status()).isEqualTo(ContentStatus.APPROVED);
    }

    @Test
    @DisplayName("콘텐츠 승인 — PENDING_REVIEW가 아니면 BusinessRuleViolationException이 발생한다")
    void approveInvalidTransition() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));

        ContentCommand.Approve command = new ContentCommand.Approve(10L, 1L, 1L, true, true, false);

        assertThatThrownBy(() -> contentService.approve(command))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("콘텐츠 반려 — PENDING_REVIEW 콘텐츠를 REJECTED로 전이한다")
    void reject() {
        sampleContent.submitForReview();
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);

        ContentCommand.Reject command = new ContentCommand.Reject(10L, 1L, 1L);
        ContentResponse.StatusChanged result = contentService.reject(command);

        assertThat(result.status()).isEqualTo(ContentStatus.REJECTED);
    }

    @Test
    @DisplayName("콘텐츠 게시 — APPROVED 콘텐츠를 PUBLISHED로 전이하고 AI 동기화를 요청한다")
    void publish() {
        sampleContent.submitForReview();
        sampleContent.approve(true, true, true);

        ArtisanProfile artisanProfile = org.mockito.Mockito.mock(ArtisanProfile.class);
        when(artisanProfile.getId()).thenReturn(1L);
        when(artisanProfile.getBusinessName()).thenReturn("도공방");
        when(artisanProfile.getCertificationLevel()).thenReturn("일반");
        when(artisanProfile.getIntroduction()).thenReturn("3대째 이천에서 청자를 굽습니다");

        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(artisanProfileRepository.findById(1L)).thenReturn(Optional.of(artisanProfile));
        when(interviewRepository.findByProductId(10L)).thenReturn(Optional.empty());

        ContentCommand.Publish command = new ContentCommand.Publish(10L, 1L);
        ContentResponse.StatusChanged result = contentService.publish(command);

        assertThat(result.status()).isEqualTo(ContentStatus.PUBLISHED);
        verify(aiContentClient).syncProduct(any());
    }

    @Test
    @DisplayName("콘텐츠 게시 — APPROVED가 아니면 BusinessRuleViolationException이 발생한다")
    void publishInvalidTransition() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));

        ContentCommand.Publish command = new ContentCommand.Publish(10L, 1L);

        assertThatThrownBy(() -> contentService.publish(command))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("문단 일괄 수정 — DRAFT 콘텐츠의 블록 전체를 교체하고 이력을 저장한다")
    void bulkUpdate() {
        ReflectionTestUtils.setField(sampleContent, "reactDocument", "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}");
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any(Content.class))).thenReturn(sampleContent);
        when(historyRepository.save(any(ContentEditHistory.class))).thenReturn(null);

        List<ContentBlock> blocks = List.of(
            new ContentBlock(0, BlockTag.h2, "소제목", null),
            new ContentBlock(1, BlockTag.p, "본문", null)
        );
        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(10L, 1L, 1L, blocks);

        ContentResponse.BulkUpdated result = contentService.bulkUpdate(command);

        assertThat(result.blocks()).hasSize(2);
        assertThat(result.blocks().get(0).tag()).isEqualTo(BlockTag.h2);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.ARTISAN);
    }

    @Test
    @DisplayName("문단 일괄 수정 — DRAFT/REJECTED가 아니면 BusinessRuleViolationException이 발생한다")
    void bulkUpdateInvalidStatus() {
        ReflectionTestUtils.setField(sampleContent, "status", ContentStatus.PENDING_REVIEW);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));

        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(10L, 1L, 1L,
            List.of(new ContentBlock(0, BlockTag.p, "텍스트", null)));

        assertThatThrownBy(() -> contentService.bulkUpdate(command))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("단건 블록 수정 — 해당 blockOrder의 블록만 변경하고 이력을 저장한다")
    void updateBlock() {
        String existingDoc = "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[" +
            "{\"tag\":\"h2\",\"props\":{},\"children\":[{\"tag\":\"text\",\"props\":{\"value\":\"기존 소제목\"},\"children\":[]}]}," +
            "{\"tag\":\"p\",\"props\":{},\"children\":[{\"tag\":\"text\",\"props\":{\"value\":\"기존 본문\"},\"children\":[]}]}" +
            "]}";
        ReflectionTestUtils.setField(sampleContent, "reactDocument", existingDoc);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any(Content.class))).thenReturn(sampleContent);
        when(historyRepository.save(any(ContentEditHistory.class))).thenReturn(null);

        ContentBlock patch = new ContentBlock(0, BlockTag.h2, "수정된 소제목", null);
        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(10L, 1L, 0, 1L, patch);

        ContentResponse.BlockUpdated result = contentService.updateBlock(command);

        assertThat(result.block().text()).isEqualTo("수정된 소제목");
        assertThat(result.block().tag()).isEqualTo(BlockTag.h2);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.ARTISAN);
    }

    @Test
    @DisplayName("단건 블록 수정 — 존재하지 않는 blockOrder면 NotFoundException이 발생한다")
    void updateBlockNotFound() {
        String existingDoc = "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[" +
            "{\"tag\":\"p\",\"props\":{},\"children\":[{\"tag\":\"text\",\"props\":{\"value\":\"본문\"},\"children\":[]}]}" +
            "]}";
        ReflectionTestUtils.setField(sampleContent, "reactDocument", existingDoc);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));

        ContentBlock patch = new ContentBlock(99, BlockTag.p, "텍스트", null);
        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(10L, 1L, 99, 1L, patch);

        assertThatThrownBy(() -> contentService.updateBlock(command))
            .isInstanceOf(NotFoundException.class);
    }
}
