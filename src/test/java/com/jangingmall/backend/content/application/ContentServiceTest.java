package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.Content;
import com.jangingmall.backend.content.domain.ContentBlock;
import com.jangingmall.backend.content.domain.ContentBlockRepository;
import com.jangingmall.backend.content.domain.ContentEditHistory;
import com.jangingmall.backend.content.domain.ContentEditHistoryRepository;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.content.domain.ContentRepository;
import com.jangingmall.backend.content.domain.ContentStatus;
import com.jangingmall.backend.content.domain.EditedByType;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.image.domain.ImageUploadRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private static final String EMPTY_DOCUMENT =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}";

    private static final String SAMPLE_DOCUMENT =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[" +
        "{\"id\":\"node-001\",\"type\":\"element\",\"tag\":\"h2\"," +
        "\"props\":{\"style\":{\"color\":\"#333\"}}," +
        "\"children\":[{\"id\":\"text-001\",\"type\":\"text\",\"value\":\"원본 제목\",\"marks\":[]}]}," +
        "{\"id\":\"node-002\",\"type\":\"element\",\"tag\":\"img\"," +
        "\"props\":{\"imageId\":\"original-img-id\"}," +
        "\"children\":[]}" +
        "]}";

    @BeforeEach
    void setUp() {
        contentService = new ContentService(
            contentRepository, historyRepository, productRepository, aiContentClient,
            artisanProfileRepository, interviewRepository, new ObjectMapper()
        );
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
    @DisplayName("콘텐츠 조회 — 두 경로(AI 사진 키 · 시연 시드 이미지 키)는 현재 이미지 기본 주소로 src를 만들고, 업로드 imageId 노드는 그대로 둔다")
    void getContentResolvesBothImagePaths() throws Exception {
        ReflectionTestUtils.setField(contentService, "imageBaseUrl", "https://img.stg.midam.store/");
        String document = "{\"root\":[{\"tag\":\"section\",\"children\":["
            // 1) AI 가 만든 사진 — 저장 때 남긴 assetKey 와 (옛 도메인의) src
            + "{\"tag\":\"img\",\"props\":{\"imageId\":\"hero\",\"assetKey\":\"ai-generated/1/photo-hero.webp\","
            + "\"src\":\"https://old.example/ai-generated/1/photo-hero.webp\"}},"
            // 2) 시연 시드 이미지(소분류 일러스트) — STG 이미지 서버에 올린 키
            + "{\"tag\":\"img\",\"props\":{\"imageId\":\"sub-25\",\"assetKey\":\"images/product/63/01M3VAY2JC89RQ9G6JXBGV7HS4/1280w.webp\"}},"
            // 3) 업로드 이미지 — imageId 만 있고 FE 가 이미지 테이블 변형으로 찾는다
            + "{\"tag\":\"img\",\"props\":{\"imageId\":\"01JUPLOADEDIMAGE00000000000\"}}]}]}";
        ReflectionTestUtils.setField(sampleContent, "reactDocument", document);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));

        ContentResponse.Detail detail = contentService.getContent(10L, 1L);

        var images = new ObjectMapper().readTree(detail.reactDocument()).get("root").get(0).get("children");
        assertThat(images.get(0).get("props").get("src").asText())
            .isEqualTo("https://img.stg.midam.store/ai-generated/1/photo-hero.webp");
        assertThat(images.get(1).get("props").get("src").asText())
            .isEqualTo("https://img.stg.midam.store/images/product/63/01M3VAY2JC89RQ9G6JXBGV7HS4/1280w.webp");
        assertThat(images.get(2).get("props").has("src")).isFalse();
        assertThat(images.get(2).get("props").get("imageId").asText()).isEqualTo("01JUPLOADEDIMAGE00000000000");
    }

    @Test
    @DisplayName("콘텐츠 조회 — 예전 AI 문서(photo_id 만 있음)는 완료된 생성 건의 ai-generated 파일을 찾아 src 를 채운다")
    void getContentResolvesLegacyAiPhotos() throws Exception {
        ImageService imageService = mock(ImageService.class);
        ContentGenerationRepository generationRepository = mock(ContentGenerationRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ContentService service = new ContentService(
            contentRepository, historyRepository, productRepository, aiContentClient,
            artisanProfileRepository, interviewRepository, null, null, imageService,
            new ObjectMapper(), generationRepository, eventPublisher
        );
        ReflectionTestUtils.setField(service, "imageBaseUrl", "https://img.stg.midam.store/");
        ContentGeneration completed = mock(ContentGeneration.class);
        when(completed.getId()).thenReturn(42L);
        when(generationRepository.findFirstByProductIdAndStatusOrderByRequestedAtDesc(10L, GenerationStatus.COMPLETED))
            .thenReturn(Optional.of(completed));
        // hero 는 webp, 나머지는 png 로 올라가 있다
        when(imageService.publicObjectExists("ai-generated/42/photo-hero.webp")).thenReturn(true);
        when(imageService.publicObjectExists("ai-generated/42/photo-detail-02.png")).thenReturn(true);
        String document = "{\"root\":[{\"tag\":\"section\",\"children\":["
            + "{\"tag\":\"img\",\"props\":{\"imageId\":\"hero\"}},"
            + "{\"tag\":\"img\",\"props\":{\"imageId\":\"detail-02\"}},"
            + "{\"tag\":\"img\",\"props\":{\"imageId\":\"missing\"}}]}]}";
        ReflectionTestUtils.setField(sampleContent, "reactDocument", document);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));

        ContentResponse.Detail detail = service.getContent(10L, 1L);

        var images = new ObjectMapper().readTree(detail.reactDocument()).get("root").get(0).get("children");
        assertThat(images.get(0).get("props").get("src").asText())
            .isEqualTo("https://img.stg.midam.store/ai-generated/42/photo-hero.webp");
        assertThat(images.get(1).get("props").get("src").asText())
            .isEqualTo("https://img.stg.midam.store/ai-generated/42/photo-detail-02.png");
        assertThat(images.get(2).get("props").has("src")).isFalse();
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

        ContentCommand.StoreReactDocument command = new ContentCommand.StoreReactDocument(10L, EMPTY_DOCUMENT, null);
        Content result = contentService.storeReactDocument(command);

        assertThat(result.getProductId()).isEqualTo(10L);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.AI);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("react_document 저장 — 업로드 이미지로 해석되지 않는 이미지 참조(AI 생성 이미지)는 그 블록만 건너뛰고 저장은 성공한다")
    void storeReactDocumentSkipsUnresolvedImage() {
        ContentBlockRepository blockRepository = mock(ContentBlockRepository.class);
        ImageUploadRepository uploadRepository = mock(ImageUploadRepository.class);
        ContentService serviceWithBlocks = new ContentService(
            contentRepository, historyRepository, productRepository, aiContentClient,
            artisanProfileRepository, interviewRepository, blockRepository, uploadRepository, null,
            new ObjectMapper(), null, null
        );
        String document = "{\"root\":[{\"tag\":\"h2\",\"text\":\"제목\"},"
            + "{\"tag\":\"img\",\"imageId\":\"ai-hero\"},{\"tag\":\"p\",\"text\":\"본문\"}]}";
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        serviceWithBlocks.storeReactDocument(new ContentCommand.StoreReactDocument(10L, document, null));

        ArgumentCaptor<Iterable<ContentBlock>> blocks = ArgumentCaptor.forClass(Iterable.class);
        verify(blockRepository).saveAll(blocks.capture());
        java.util.List<String> tags = new java.util.ArrayList<>();
        blocks.getValue().forEach(block -> tags.add(block.getTag()));
        assertThat(tags).containsExactly("h2", "p");
        verify(contentRepository).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue().getReactDocument()).isEqualTo(document);
    }

    @Test
    @DisplayName("늦은 AI 결과 덮어쓰기 — 콘텐츠가 없으면 허용한다")
    void canOverwriteWithAiResultWhenNoContent() {
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.empty());

        assertThat(contentService.canOverwriteWithAiResult(10L)).isTrue();
    }

    @Test
    @DisplayName("늦은 AI 결과 덮어쓰기 — AI 이력뿐인 DRAFT 콘텐츠는 허용한다")
    void canOverwriteWithAiResultWhenOnlyAiHistory() {
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(historyRepository.findAllByContentIdOrderByVersionAsc(1L))
            .thenReturn(java.util.List.of(ContentEditHistory.record(1L, 1, EditedByType.AI, null)));

        assertThat(contentService.canOverwriteWithAiResult(10L)).isTrue();
    }

    @Test
    @DisplayName("늦은 AI 결과 덮어쓰기 — 사용자가 수정한 이력이 있으면 거절한다")
    void cannotOverwriteWithAiResultWhenArtisanEdited() {
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(historyRepository.findAllByContentIdOrderByVersionAsc(1L))
            .thenReturn(java.util.List.of(
                ContentEditHistory.record(1L, 1, EditedByType.AI, null),
                ContentEditHistory.record(1L, 2, EditedByType.ARTISAN, 1L)));

        assertThat(contentService.canOverwriteWithAiResult(10L)).isFalse();
    }

    @Test
    @DisplayName("늦은 AI 결과 덮어쓰기 — 검수 중(DRAFT가 아닌) 콘텐츠는 거절한다")
    void cannotOverwriteWithAiResultWhenNotDraft() {
        sampleContent.submitForReview();
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));

        assertThat(contentService.canOverwriteWithAiResult(10L)).isFalse();
    }

    @Test
    @DisplayName("react_document 저장 — 기존 콘텐츠가 있으면 덮어쓴다")
    void storeReactDocumentUpdate() {
        sampleContent.storeReactDocument("{\"schemaVersion\":\"1.0\"}");
        when(contentRepository.findByProductId(10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(historyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentCommand.StoreReactDocument command = new ContentCommand.StoreReactDocument(10L, EMPTY_DOCUMENT, null);
        contentService.storeReactDocument(command);

        verify(contentRepository).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue().getReactDocument()).isEqualTo(EMPTY_DOCUMENT);
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
    @DisplayName("콘텐츠 승인 — DRAFT_READY generation이 있으면 AiRenderApprovalRequestedEvent를 발행한다")
    void approvePublishesRenderApprovalEvent() {
        sampleContent.submitForReview();
        ContentGenerationRepository generationRepository = mock(ContentGenerationRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        ContentService serviceWithGeneration = new ContentService(
            contentRepository, historyRepository, productRepository, aiContentClient,
            artisanProfileRepository, interviewRepository, null, null, null,
            new ObjectMapper(), generationRepository, eventPublisher
        );

        ContentGeneration draftReady = mock(ContentGeneration.class);
        when(draftReady.getJobId()).thenReturn("job-xyz");
        when(draftReady.getId()).thenReturn(42L);

        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(generationRepository.findFirstByProductIdAndStatusOrderByRequestedAtDesc(10L, GenerationStatus.DRAFT_READY))
            .thenReturn(Optional.of(draftReady));

        serviceWithGeneration.approve(new ContentCommand.Approve(10L, 1L, 1L, true, true, false));

        ArgumentCaptor<AiRenderApprovalRequestedEvent> captor =
            ArgumentCaptor.forClass(AiRenderApprovalRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo("job-xyz");
        assertThat(captor.getValue().generationId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("콘텐츠 승인 — DRAFT_READY generation이 없으면 이벤트를 발행하지 않는다")
    void approveDoesNotPublishEventWhenNoDraftReady() {
        sampleContent.submitForReview();
        ContentGenerationRepository generationRepository = mock(ContentGenerationRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        ContentService serviceWithGeneration = new ContentService(
            contentRepository, historyRepository, productRepository, aiContentClient,
            artisanProfileRepository, interviewRepository, null, null, null,
            new ObjectMapper(), generationRepository, eventPublisher
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any())).thenReturn(sampleContent);
        when(generationRepository.findFirstByProductIdAndStatusOrderByRequestedAtDesc(10L, GenerationStatus.DRAFT_READY))
            .thenReturn(Optional.empty());

        serviceWithGeneration.approve(new ContentCommand.Approve(10L, 1L, 1L, true, true, false));

        verify(eventPublisher, never()).publishEvent(any(AiRenderApprovalRequestedEvent.class));
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
        when(artisanProfile.getRegion()).thenReturn("경기 이천");

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
    @DisplayName("문단 일괄 수정 — AI 생성 스타일을 보존하며 텍스트와 imageId를 교체한다")
    void bulkUpdate() {
        ReflectionTestUtils.setField(sampleContent, "reactDocument", SAMPLE_DOCUMENT);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any(Content.class))).thenReturn(sampleContent);
        when(historyRepository.save(any(ContentEditHistory.class))).thenReturn(null);

        List<ContentCommand.NodePatch> patches = List.of(
            new ContentCommand.NodePatch("text-001", "변경된 제목", null),
            new ContentCommand.NodePatch("node-002", null, "new-img-id")
        );
        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(10L, 1L, 1L, patches);

        ContentResponse.BulkUpdated result = contentService.bulkUpdate(command);

        assertThat(result.contentId()).isEqualTo(1L);
        verify(contentRepository).save(contentCaptor.capture());
        String savedDoc = contentCaptor.getValue().getReactDocument();
        assertThat(savedDoc).contains("변경된 제목");
        assertThat(savedDoc).contains("new-img-id");
        assertThat(savedDoc).contains("\"color\":\"#333\"");
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.ARTISAN);
    }

    @Test
    @DisplayName("문단 일괄 수정 — DRAFT/REJECTED가 아니면 BusinessRuleViolationException이 발생한다")
    void bulkUpdateInvalidStatus() {
        ReflectionTestUtils.setField(sampleContent, "status", ContentStatus.PENDING_REVIEW);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));

        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(
            10L, 1L, 1L,
            List.of(new ContentCommand.NodePatch("any-id", "text", null))
        );

        assertThatThrownBy(() -> contentService.bulkUpdate(command))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("단건 블록 수정 — nodeId로 특정 노드의 텍스트를 교체하고 이력을 저장한다")
    void updateBlock() {
        ReflectionTestUtils.setField(sampleContent, "reactDocument", SAMPLE_DOCUMENT);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));
        when(contentRepository.save(any(Content.class))).thenReturn(sampleContent);
        when(historyRepository.save(any(ContentEditHistory.class))).thenReturn(null);

        ContentCommand.NodePatch patch = new ContentCommand.NodePatch("text-001", "새 제목", null);
        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(10L, 1L, "text-001", 1L, patch);

        ContentResponse.BlockUpdated result = contentService.updateBlock(command);

        assertThat(result.nodeId()).isEqualTo("text-001");
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getEditedByType()).isEqualTo(EditedByType.ARTISAN);
    }

    @Test
    @DisplayName("단건 블록 수정 — 존재하지 않는 nodeId면 NotFoundException이 발생한다")
    void updateBlockNotFound() {
        ReflectionTestUtils.setField(sampleContent, "reactDocument", SAMPLE_DOCUMENT);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(contentRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(sampleContent));

        ContentCommand.NodePatch patch = new ContentCommand.NodePatch("non-existent-id", "text", null);
        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(10L, 1L, "non-existent-id", 1L, patch);

        assertThatThrownBy(() -> contentService.updateBlock(command))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("AI 상품 일괄 동기화 — ON_SALE 상품 ID를 전부 조회해 각각 syncPublishedProductToAi를 호출한다")
    void bulkSyncPublishedProductsToAi_callsSyncForEachOnSaleProduct() {
        ArtisanProfile artisanProfile = org.mockito.Mockito.mock(ArtisanProfile.class);
        when(artisanProfile.getId()).thenReturn(1L);
        when(artisanProfile.getBusinessName()).thenReturn("도공방");
        when(artisanProfile.getCertificationLevel()).thenReturn("일반");
        when(artisanProfile.getRegion()).thenReturn("경기 이천");

        when(productRepository.findAllOnSaleIds()).thenReturn(List.of(10L, 20L));
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(productRepository.findById(20L)).thenReturn(Optional.of(artisanProduct));
        when(artisanProfileRepository.findById(any())).thenReturn(Optional.of(artisanProfile));
        when(interviewRepository.findByProductId(any())).thenReturn(Optional.empty());

        int synced = contentService.bulkSyncPublishedProductsToAi();

        assertThat(synced).isEqualTo(2);
        verify(aiContentClient, org.mockito.Mockito.times(2)).syncProduct(any());
    }

    @Test
    @DisplayName("AI 상품 일괄 동기화 — 개별 상품 동기화 실패 시 나머지 상품은 계속 처리된다")
    void bulkSyncPublishedProductsToAi_continuesOnPartialFailure() {
        ArtisanProfile artisanProfile = org.mockito.Mockito.mock(ArtisanProfile.class);
        when(artisanProfile.getId()).thenReturn(1L);
        when(artisanProfile.getBusinessName()).thenReturn("도공방");
        when(artisanProfile.getCertificationLevel()).thenReturn("일반");
        when(artisanProfile.getRegion()).thenReturn("경기 이천");

        when(productRepository.findAllOnSaleIds()).thenReturn(List.of(10L, 99L));
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(productRepository.findById(99L)).thenThrow(new NotFoundException("상품 없음"));
        when(artisanProfileRepository.findById(any())).thenReturn(Optional.of(artisanProfile));
        when(interviewRepository.findByProductId(any())).thenReturn(Optional.empty());

        int synced = contentService.bulkSyncPublishedProductsToAi();

        assertThat(synced).isEqualTo(1);
        verify(aiContentClient, org.mockito.Mockito.times(1)).syncProduct(any());
    }
}
