package com.jangingmall.backend.content.application;

import tools.jackson.databind.ObjectMapper;
import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import org.mockito.Mockito;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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

    @Captor
    private ArgumentCaptor<ContentGeneration> generationCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GenerationService generationService;
    private Product artisanProduct;

    @BeforeEach
    void setUp() {
        generationService = org.mockito.Mockito.spy(new GenerationService(generationRepository, productRepository, aiContentClient, contentService, objectMapper));
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
        // 단위 테스트에서는 @Async 미적용으로 executeAsync가 동기 실행됨 — stub으로 격리
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
    @DisplayName("AI 호출 성공 시 executeAsync가 블록 JSON과 함께 COMPLETED로 전환한다")
    void executeAsyncSuccess() throws Exception {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        String blocks = objectMapper.writeValueAsString(
            List.of(Map.of("order", 1, "tag", "h2", "text", "청자 다완"))
        );
        when(aiContentClient.requestGeneration(any(), any(), any(), any(), any(), any())).thenReturn(blocks);

        generationService.executeAsync(1L, sampleCommand(1L));

        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(generationCaptor.getValue().getGeneratedBlocks()).isEqualTo(blocks);
        assertThat(generationCaptor.getValue().getCompletedAt()).isNotNull();
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
}
