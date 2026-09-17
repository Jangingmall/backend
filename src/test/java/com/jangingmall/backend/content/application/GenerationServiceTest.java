package com.jangingmall.backend.content.application;

import tools.jackson.databind.ObjectMapper;
import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.image.application.ImageStorage;
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

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock
    private ContentGenerationRepository generationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AiContentClient aiContentClient;
    @Mock
    private ContentService contentService;
    @Mock
    private ImageStorage imageStorage;

    @Captor
    private ArgumentCaptor<ContentGeneration> generationCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GenerationService generationService;
    private Product artisanProduct;

    private static final String REACT_DOCUMENT_JSON =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}";

    @BeforeEach
    void setUp() {
        generationService = spy(new GenerationService(generationRepository, productRepository, aiContentClient, contentService, imageStorage, objectMapper));
        artisanProduct = Product.create(1L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(artisanProduct, "id", 10L);
    }

    private GenerationCommand.Request sampleCommand(Long requesterId) {
        return new GenerationCommand.Request(10L, requesterId, List.of("imageId1", "imageId2"), "청자 다완", "손으로 빚음", "물 닦기");
    }

    @Test
    @DisplayName("AI 생성 요청 시 PROCESSING 상태로 저장된다")
    void request() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        ContentGeneration saved = ContentGeneration.create(10L, "imageId1,imageId2", "청자 다완", "손으로 빚음", "물 닦기");
        ReflectionTestUtils.setField(saved, "id", 1L);
        when(generationRepository.save(any())).thenReturn(saved);
        doNothing().when(generationService).executeAsync(anyLong(), any());

        GenerationResponse response = generationService.request(sampleCommand(1L));

        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(GenerationStatus.PROCESSING);
        assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 생성 요청하면 ForbiddenException이 발생한다")
    void requestForbidden() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        assertThatThrownBy(() -> generationService.request(sampleCommand(999L)))
            .isInstanceOf(ForbiddenException.class);
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
    @DisplayName("AI 호출 성공 시 executeAsync가 react_document와 함께 COMPLETED로 전환하고 blob을 저장한다")
    void executeAsyncSuccess() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        when(aiContentClient.requestGeneration(any(), any(), any(), any(), any(), any())).thenReturn(REACT_DOCUMENT_JSON);

        generationService.executeAsync(1L, sampleCommand(1L));

        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(generationCaptor.getValue().getReactDocument()).isEqualTo(REACT_DOCUMENT_JSON);
        assertThat(generationCaptor.getValue().getCompletedAt()).isNotNull();
        verify(contentService).storeReactDocument(any());
    }

    @Test
    @DisplayName("AI 호출 실패 시 executeAsync가 FAILED로 전환하고 예외를 삼키지 않는다")
    void executeAsyncFailed() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        doThrow(new RuntimeException("AI 서버 응답 없음")).when(aiContentClient).requestGeneration(any(), any(), any(), any(), any(), any());

        generationService.executeAsync(1L, sampleCommand(1L));

        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(generationCaptor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("AI 타임아웃 시 FAILED로 전환된다")
    void executeAsyncTimeout() {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        doThrow(new RuntimeException("Read timeout")).when(aiContentClient).requestGeneration(any(), any(), any(), any(), any(), any());

        generationService.executeAsync(1L, sampleCommand(1L));

        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
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
        when(generationRepository.findById(1L)).thenReturn(Optional.of(generation));
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
}
