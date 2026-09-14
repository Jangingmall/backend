package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.Interview;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.ConflictException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private ProductRepository productRepository;

    @Captor
    private ArgumentCaptor<Interview> interviewCaptor;

    private InterviewService interviewService;

    private Product artisanProduct;

    @BeforeEach
    void setUp() {
        interviewService = new InterviewService(interviewRepository, productRepository);
        artisanProduct = Product.create(1L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(artisanProduct, "id", 10L);
    }

    @Test
    @DisplayName("취재 데이터를 등록하면 저장된다")
    void create() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(interviewRepository.existsByProductId(10L)).thenReturn(false);
        when(interviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewCommand.Create command = new InterviewCommand.Create(10L, 1L, "과정", "청자", "청자기법", "스토리");
        InterviewResponse response = interviewService.create(command);

        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.process()).isEqualTo("과정");
    }

    @Test
    @DisplayName("취재 데이터가 이미 존재하면 ConflictException이 발생한다")
    void createDuplicate() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(interviewRepository.existsByProductId(10L)).thenReturn(true);

        InterviewCommand.Create command = new InterviewCommand.Create(10L, 1L, "과정", "청자", "기법", "스토리");

        assertThatThrownBy(() -> interviewService.create(command))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 취재 데이터를 등록하면 ForbiddenException이 발생한다")
    void createByNonOwner() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        InterviewCommand.Create command = new InterviewCommand.Create(10L, 999L, "과정", "청자", "기법", "스토리");

        assertThatThrownBy(() -> interviewService.create(command))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품에 취재 데이터를 등록하면 NotFoundException이 발생한다")
    void createProductNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        InterviewCommand.Create command = new InterviewCommand.Create(999L, 1L, "과정", "청자", "기법", "스토리");

        assertThatThrownBy(() -> interviewService.create(command))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("취재 데이터를 조회한다")
    void find() {
        Interview interview = Interview.create(10L, "과정", "청자", "기법", "스토리");
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(interviewRepository.findByProductId(10L)).thenReturn(Optional.of(interview));

        InterviewResponse response = interviewService.find(10L, 1L);

        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.materials()).isEqualTo("청자");
    }

    @Test
    @DisplayName("취재 데이터가 없으면 조회 시 NotFoundException이 발생한다")
    void findNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(interviewRepository.findByProductId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.find(10L, 1L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 조회하면 ForbiddenException이 발생한다")
    void findByNonOwner() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        assertThatThrownBy(() -> interviewService.find(10L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("취재 데이터를 부분 수정한다")
    void update() {
        Interview interview = Interview.create(10L, "원래 과정", "원래 소재", "원래 기법", "원래 스토리");
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(interviewRepository.findByProductId(10L)).thenReturn(Optional.of(interview));

        InterviewCommand.Update command = new InterviewCommand.Update(10L, 1L, null, "새 소재", null, null);
        InterviewResponse response = interviewService.update(command);

        assertThat(response.process()).isEqualTo("원래 과정");
        assertThat(response.materials()).isEqualTo("새 소재");
    }

    @Test
    @DisplayName("취재 데이터가 없으면 수정 시 NotFoundException이 발생한다")
    void updateNotFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));
        when(interviewRepository.findByProductId(10L)).thenReturn(Optional.empty());

        InterviewCommand.Update command = new InterviewCommand.Update(10L, 1L, "과정", "소재", "기법", "스토리");

        assertThatThrownBy(() -> interviewService.update(command))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("소유자가 아닌 장인이 수정하면 ForbiddenException이 발생한다")
    void updateByNonOwner() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(artisanProduct));

        InterviewCommand.Update command = new InterviewCommand.Update(10L, 999L, "과정", "소재", "기법", "스토리");

        assertThatThrownBy(() -> interviewService.update(command))
            .isInstanceOf(ForbiddenException.class);
    }
}
