package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.BlockTag;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
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
    private ContentBlockRepository contentBlockRepository;
    @Mock
    private ContentEditHistoryRepository historyRepository;
    @Mock
    private ProductRepository productRepository;

    @Captor
    private ArgumentCaptor<Content> contentCaptor;
    @Captor
    private ArgumentCaptor<ContentEditHistory> historyCaptor;

    private ContentService contentService;
    private Product artisanProduct;
    private Content sampleContent;

    @BeforeEach
    void setUp() {
        contentService = new ContentService(contentRepository, contentBlockRepository, historyRepository, productRepository);
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
    @DisplayName("문단 일괄 수정 — 블록 목록을 교체하고 ARTISAN 이력을 기록한다")
    void bulkUpdateBlocks() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ContentCommand.BlockInput> blocks = List.of(
            new ContentCommand.BlockInput(1, BlockTag.h2, "소제목", null, null),
            new ContentCommand.BlockInput(2, BlockTag.p, "본문", null, null)
        );
        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(10L, 1L, 1L, blocks);
        ContentResponse.Detail result = contentService.bulkUpdateBlocks(command);

        assertThat(result.contentId()).isEqualTo(1L);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.ARTISAN);
        assertThat(historyCaptor.getValue().getEditedByMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("문단 일괄 수정 — 소유자가 아니면 ForbiddenException이 발생한다")
    void bulkUpdateForbidden() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(10L, 1L, 999L, List.of());

        assertThatThrownBy(() -> contentService.bulkUpdateBlocks(command))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("문단 일괄 수정 — 콘텐츠가 없으면 NotFoundException이 발생한다")
    void bulkUpdateNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.empty());

        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(10L, 1L, 1L, List.of());

        assertThatThrownBy(() -> contentService.bulkUpdateBlocks(command))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("단건 블록 수정 — 블록을 수정하고 version을 증가시키며 이력을 기록한다")
    void updateBlock() {
        ContentBlock block = ContentBlock.create(sampleContent, (short) 1, BlockTag.p, null, null, "원래 텍스트");
        ReflectionTestUtils.setField(block, "id", 100L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentBlockRepository.findByContentIdAndDisplayOrder(1L, (short) 1)).thenReturn(Optional.of(block));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(10L, 1L, 1, 1L, BlockTag.p, "새 텍스트", null);
        ContentResponse.BlockEdit result = contentService.updateBlock(command);

        assertThat(result.contentId()).isEqualTo(1L);
        assertThat(result.block().tag()).isEqualTo("p");
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.ARTISAN);
    }

    @Test
    @DisplayName("단건 블록 수정 — 블록이 없으면 NotFoundException이 발생한다")
    void updateBlockNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentBlockRepository.findByContentIdAndDisplayOrder(1L, (short) 1)).thenReturn(Optional.empty());

        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(10L, 1L, 1, 1L, null, "텍스트", null);

        assertThatThrownBy(() -> contentService.updateBlock(command))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("버전 이력 조회 — 이력을 오름차순으로 반환한다")
    void getVersionHistory() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        ContentEditHistory h1 = ContentEditHistory.record(1L, 1, EditedByType.AI, null);
        ContentEditHistory h2 = ContentEditHistory.record(1L, 2, EditedByType.ARTISAN, 1L);
        when(historyRepository.findAllByContentIdOrderByVersionAsc(1L)).thenReturn(List.of(h1, h2));

        List<ContentResponse.VersionHistory> history = contentService.getVersionHistory(10L, 1L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).editedBy()).isEqualTo(EditedByType.AI);
        assertThat(history.get(1).editedBy()).isEqualTo(EditedByType.ARTISAN);
    }

    @Test
    @DisplayName("AI 정규화 — 블록 목록으로 Content를 생성하고 AI 이력을 기록한다")
    void materializeFromBlocks() {
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ContentCommand.BlockInput> blocks = List.of(
            new ContentCommand.BlockInput(1, BlockTag.h2, "소제목", null, null)
        );
        Content result = contentService.materializeFromBlocks(10L, blocks, 1L);

        assertThat(result.getProductId()).isEqualTo(10L);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.AI);
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
    @DisplayName("콘텐츠 게시 — APPROVED 콘텐츠를 PUBLISHED로 전이한다")
    void publish() {
        sampleContent.submitForReview();
        sampleContent.approve(true, true, true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);

        ContentCommand.Publish command = new ContentCommand.Publish(10L, 1L);
        ContentResponse.StatusChanged result = contentService.publish(command);

        assertThat(result.status()).isEqualTo(ContentStatus.PUBLISHED);
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
}
