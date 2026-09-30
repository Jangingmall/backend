package com.jangingmall.backend.content.application;

import tools.jackson.databind.ObjectMapper;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationFailureReason;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ConflictException;
import com.jangingmall.backend.global.exception.ExternalServiceException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.image.application.ImageStorage;
import com.jangingmall.backend.image.domain.ImagePurpose;
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
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock
    private ContentGenerationRepository generationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ContentService contentService;
    @Mock
    private ImageStorage imageStorage;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<ContentGeneration> generationCaptor;
    @Captor
    private ArgumentCaptor<GenerationRequestedEvent> eventCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GenerationService generationService;
    private Product artisanProduct;

    private static final String REACT_DOCUMENT_JSON =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}";

    @BeforeEach
    void setUp() {
        generationService = new GenerationService(
            generationRepository, productRepository, contentService, imageStorage, objectMapper, eventPublisher
        );
        artisanProduct = Product.create(1L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(artisanProduct, "id", 10L);
    }

    private GenerationCommand.Request sampleCommand(Long requesterId) {
        return new GenerationCommand.Request(10L, requesterId, List.of("imageId1", "imageId2"), "청자 다완", "손으로 빚음", "물 닦기");
    }

    @Test
    @DisplayName("AI 생성 요청 시 PROCESSING 상태로 저장되고 GenerationRequestedEvent가 발행된다")
    void request() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        ContentGeneration saved = ContentGeneration.create(10L, "imageId1,imageId2", "청자 다완", "손으로 빚음", "물 닦기");
        ReflectionTestUtils.setField(saved, "id", 1L);
        when(generationRepository.save(any())).thenReturn(saved);

        GenerationResponse response = generationService.request(sampleCommand(1L));

        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(GenerationStatus.PROCESSING);
        assertThat(response.completedAt()).isNull();
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().generationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 생성 요청하면 ForbiddenException이 발생한다")
    void requestForbidden() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        assertThatThrownBy(() -> generationService.request(sampleCommand(999L)))
            .isInstanceOf(ForbiddenException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("존재하지 않는 상품에 생성 요청하면 NotFoundException이 발생한다")
    void requestProductNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generationService.request(sampleCommand(1L)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("상태 조회 시 현재 상태를 반환한다")
    void poll() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));

        GenerationResponse response = generationService.poll(10L, 1L, 1L);

        assertThat(response.generationId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(GenerationStatus.PROCESSING);
    }

    @Test
    @DisplayName("존재하지 않는 생성 요청 조회 시 NotFoundException이 발생한다")
    void pollNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(generationRepository.findByIdAndProductId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generationService.poll(10L, 999L, 1L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("AI 콜백 — complete() 호출 시 COMPLETED로 전환하고 react_document를 저장한다")
    void complete() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findById(1L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);

        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);
        GenerationResponse response = generationService.complete(command);

        assertThat(response.status()).isEqualTo(GenerationStatus.COMPLETED);
        verify(contentService).storeReactDocument(any());
    }

    @Test
    @DisplayName("AI 콜백 — 존재하지 않는 generationId로 complete 호출 시 NotFoundException이 발생한다")
    void completeNotFound() {
        when(generationRepository.findById(999L)).thenReturn(Optional.empty());

        GenerationCommand.Complete command = new GenerationCommand.Complete(999L, "idem-key", REACT_DOCUMENT_JSON);

        assertThatThrownBy(() -> generationService.complete(command))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("멀티파트 콜백 — completeWithImages() 호출 시 이미지 업로드 후 COMPLETED로 전환한다")
    void completeWithImages() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.empty());
        when(generationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);

        MultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Map<String, MultipartFile> photoFiles = Map.of(
            "product_photo_hero", new MockMultipartFile("product_photo_hero", "hero.jpg", "image/jpeg", new byte[]{4, 5, 6})
        );

        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);
        BeToAiPersistAckResponse ack = generationService.completeWithImages(command, detailImage, Map.of(), photoFiles, "10");

        assertThat(ack.status()).isEqualTo("SAVED");
        assertThat(ack.generationId()).isEqualTo("1");
        verify(contentService).storeReactDocument(any());
    }

    @Test
    @DisplayName("멀티파트 콜백 — 상세페이지 이미지 업로드 실패 시 ExternalServiceException이 발생한다")
    void completeWithImagesDetailUploadFails() throws Exception {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.empty());
        when(generationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generation));

        MultipartFile detailImage = Mockito.mock(MultipartFile.class);
        when(detailImage.isEmpty()).thenReturn(false);
        when(detailImage.getContentType()).thenReturn("image/jpeg");
        when(detailImage.getBytes()).thenThrow(new java.io.IOException("S3 연결 실패"));

        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);

        assertThatThrownBy(() -> generationService.completeWithImages(command, detailImage, Map.of(), Map.of(), "10"))
            .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("멀티파트 콜백 — 섹션 파일 업로드 실패 시 ExternalServiceException이 발생한다")
    void completeWithImagesSectionUploadFails() throws Exception {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.empty());
        when(generationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generation));

        MultipartFile sectionFile = Mockito.mock(MultipartFile.class);
        when(sectionFile.getContentType()).thenReturn("image/jpeg");
        when(sectionFile.getOriginalFilename()).thenReturn("section-1.jpg");
        when(sectionFile.getBytes()).thenThrow(new java.io.IOException("S3 연결 실패"));

        MultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", "image/jpeg", new byte[]{1});

        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);

        assertThatThrownBy(() -> generationService.completeWithImages(
                command, detailImage, Map.of("detail_page_section_1", sectionFile), Map.of(), "10"))
            .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    @DisplayName("멀티파트 콜백 — 이미 완료된 idempotencyKey면 ALREADY_SAVED를 반환한다")
    void completeWithImagesIdempotent() {
        ContentGeneration existing = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(existing, "id", 1L);
        existing.complete(REACT_DOCUMENT_JSON, "idem-key");
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.of(existing));

        MultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", "image/jpeg", new byte[]{1});
        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);
        BeToAiPersistAckResponse ack = generationService.completeWithImages(command, detailImage, Map.of(), Map.of(), "10");

        assertThat(ack.status()).isEqualTo("ALREADY_SAVED");
    }

    @Test
    @DisplayName("멀티파트 콜백 — 사진은 photo_id 전체, 섹션은 순번으로 S3 키를 만들어 서로 덮어쓰지 않는다")
    void completeWithImagesKeepsFileKeysDistinct() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.empty());
        when(generationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);

        Map<String, MultipartFile> photoFiles = new java.util.LinkedHashMap<>();
        photoFiles.put("product_photo_03", new MockMultipartFile(
            "product_photo_03", "1-photo-03-detail-02.webp", "image/webp", new byte[]{1}));
        photoFiles.put("product_photo_04", new MockMultipartFile(
            "product_photo_04", "1-photo-04-detail-03.webp", "image/webp", new byte[]{2}));
        Map<String, MultipartFile> sectionFiles = Map.of(
            "detail_page_section_01", new MockMultipartFile(
                "detail_page_section_01", "1-01-hero.png", "image/png", new byte[]{3}));

        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);
        generationService.completeWithImages(command, null, sectionFiles, photoFiles, "10");

        verify(imageStorage).put(eq(ImagePurpose.PRODUCT), eq("ai-generated/1/photo-detail-02.webp"), eq("image/webp"), any());
        verify(imageStorage).put(eq(ImagePurpose.PRODUCT), eq("ai-generated/1/photo-detail-03.webp"), eq("image/webp"), any());
        verify(imageStorage).put(eq(ImagePurpose.PRODUCT), eq("ai-generated/1/section-01.png"), eq("image/png"), any());
    }

    // ── 렌더링 수동 요청 ────────────────────────────────────────────────────────

    private ContentGeneration draftReadyGeneration() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        generation.markQueued("job-1", "req-1", "1", "http://ai/status/1");
        generation.markDraftReady();
        ReflectionTestUtils.setField(generation, "id", 1L);
        return generation;
    }

    @Test
    @DisplayName("렌더링 수동 요청 — DRAFT_READY이면 AiRenderApprovalRequestedEvent를 발행하고 상태는 그대로 반환한다")
    void requestRender() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(draftReadyGeneration()));

        GenerationResponse response = generationService.requestRender(10L, 1L, 1L);

        assertThat(response.status()).isEqualTo(GenerationStatus.DRAFT_READY);
        ArgumentCaptor<AiRenderApprovalRequestedEvent> captor =
            ArgumentCaptor.forClass(AiRenderApprovalRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo("job-1");
        assertThat(captor.getValue().generationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("렌더링 수동 요청 — DRAFT_READY가 아니면 BusinessRuleViolationException이 발생하고 이벤트를 발행하지 않는다")
    void requestRenderNotDraftReady() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));

        assertThatThrownBy(() -> generationService.requestRender(10L, 1L, 1L))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("렌더링 수동 요청 — 소유자가 아니면 ForbiddenException이 발생한다")
    void requestRenderForbidden() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        assertThatThrownBy(() -> generationService.requestRender(10L, 1L, 999L))
            .isInstanceOf(ForbiddenException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("렌더링 수동 요청 — 존재하지 않는 생성 요청이면 NotFoundException이 발생한다")
    void requestRenderNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(generationRepository.findByIdAndProductId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generationService.requestRender(10L, 999L, 1L))
            .isInstanceOf(NotFoundException.class);
    }

    private ContentGeneration failedGeneration(GenerationFailureReason reason) {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        generation.fail(reason);
        return generation;
    }

    private BeToAiPersistAckResponse completeLate(ContentGeneration generation) {
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.empty());
        when(generationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generation));
        MultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", "image/jpeg", new byte[]{1});
        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);
        return generationService.completeWithImages(command, detailImage, Map.of(), Map.of(), "10");
    }

    @Test
    @DisplayName("멀티파트 콜백 — 마감 초과로 FAILED가 된 건에 결과가 늦게 도착하면 COMPLETED로 되돌려 저장한다")
    void completeWithImagesAcceptsLateResultAfterDeadline() {
        ContentGeneration generation = failedGeneration(GenerationFailureReason.RENDER_DEADLINE);
        when(contentService.canOverwriteWithAiResult(10L)).thenReturn(true);
        when(generationRepository.save(any())).thenReturn(generation);

        BeToAiPersistAckResponse ack = completeLate(generation);

        assertThat(ack.status()).isEqualTo("SAVED");
        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(generation.getFailureReason()).isNull();
        verify(imageStorage).put(eq(ImagePurpose.PRODUCT), eq("ai-generated/1/detail-page.jpg"), eq("image/jpeg"), any());
        verify(contentService).storeReactDocument(any());
    }

    @Test
    @DisplayName("멀티파트 콜백 — AI가 실패를 확정한 건의 콜백은 저장하지 않고 ConflictException을 던진다")
    void completeWithImagesRejectsAiFailed() {
        ContentGeneration generation = failedGeneration(GenerationFailureReason.AI_FAILED);

        assertThatThrownBy(() -> completeLate(generation)).isInstanceOf(ConflictException.class);

        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FAILED);
        verify(imageStorage, never()).put(any(), any(), any(), any());
        verify(contentService, never()).storeReactDocument(any());
    }

    @Test
    @DisplayName("멀티파트 콜백 — 같은 상품에 더 새로운 생성이 있으면 늦은 결과를 거절한다")
    void completeWithImagesRejectsWhenNewerGenerationExists() {
        ContentGeneration generation = failedGeneration(GenerationFailureReason.RENDER_DEADLINE);
        when(generationRepository.existsNewerNonFailed(eq(10L), any())).thenReturn(true);

        assertThatThrownBy(() -> completeLate(generation)).isInstanceOf(ConflictException.class);

        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FAILED);
        verify(imageStorage, never()).put(any(), any(), any(), any());
        verify(contentService, never()).storeReactDocument(any());
    }

    @Test
    @DisplayName("멀티파트 콜백 — 사용자가 이미 콘텐츠를 수정했거나 검수 중이면 늦은 결과를 거절한다")
    void completeWithImagesRejectsWhenContentEdited() {
        ContentGeneration generation = failedGeneration(GenerationFailureReason.AI_DEADLINE);
        when(contentService.canOverwriteWithAiResult(10L)).thenReturn(false);

        assertThatThrownBy(() -> completeLate(generation)).isInstanceOf(ConflictException.class);

        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FAILED);
        verify(imageStorage, never()).put(any(), any(), any(), any());
        verify(contentService, never()).storeReactDocument(any());
    }

    @Test
    @DisplayName("멀티파트 콜백 — 잠금을 잡은 뒤 이미 COMPLETED면(동시 콜백) 저장하지 않고 ALREADY_SAVED를 반환한다")
    void completeWithImagesConcurrentDuplicate() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        generation.complete(REACT_DOCUMENT_JSON, "other-key");
        when(generationRepository.findByIdempotencyKey("idem-key")).thenReturn(Optional.empty());
        when(generationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generation));

        MultipartFile detailImage = new MockMultipartFile("detail_page_image", "detail.jpg", "image/jpeg", new byte[]{1});
        GenerationCommand.Complete command = new GenerationCommand.Complete(1L, "idem-key", REACT_DOCUMENT_JSON);
        BeToAiPersistAckResponse ack = generationService.completeWithImages(command, detailImage, Map.of(), Map.of(), "10");

        assertThat(ack.status()).isEqualTo("ALREADY_SAVED");
        verify(imageStorage, never()).put(any(), any(), any(), any());
        verify(contentService, never()).storeReactDocument(any());
    }
}
