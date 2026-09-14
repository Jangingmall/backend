package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductAnswer;
import com.jangingmall.backend.product.domain.ProductAnswerRepository;
import com.jangingmall.backend.product.domain.ProductQuestion;
import com.jangingmall.backend.product.domain.ProductQuestionRepository;
import com.jangingmall.backend.product.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQnaServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductQuestionRepository questionRepository;
    @Mock
    private ProductAnswerRepository answerRepository;

    private ProductQnaService service;

    private Product product;

    @BeforeEach
    void setUp() {
        service = new ProductQnaService(productRepository, questionRepository, answerRepository);
        product = Product.create(10L, null, null, "청자 다완", "설명", 85000, 10, null);
        ReflectionTestUtils.setField(product, "id", 1L);
        ReflectionTestUtils.setField(product, "artisanId", 10L);
    }

    @Test
    @DisplayName("문의 목록 조회 시 공개 문의 내용을 반환한다")
    void findQuestions_returnsVisibleContent() {
        ProductQuestion question = ProductQuestion.ask(1L, 99L, "공개 질문", false);
        ReflectionTestUtils.setField(question, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(questionRepository.findByProductId(anyLong(), any())).thenReturn(new PageImpl<>(List.of(question)));
        when(answerRepository.findByQuestionId(anyLong())).thenReturn(Optional.empty());

        Page<ProductQnaResponse.QuestionView> result = service.findQuestions(1L, 99L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).content()).isEqualTo("공개 질문");
    }

    @Test
    @DisplayName("문의 등록 시 저장된 문의를 반환한다")
    void ask_savesQuestion() {
        ProductQuestion question = ProductQuestion.ask(1L, 99L, "질문입니다", false);
        ReflectionTestUtils.setField(question, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(questionRepository.save(any())).thenReturn(question);

        ProductQnaResponse.QuestionView result = service.ask(new ProductQnaCommand.Ask(1L, 99L, "질문입니다", false));

        assertThat(result.questionId()).isEqualTo(1L);
        assertThat(result.content()).isEqualTo("질문입니다");
    }

    @Test
    @DisplayName("존재하지 않는 상품에 문의하면 NotFoundException이 발생한다")
    void ask_productNotFound() {
        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(new ProductQnaCommand.Ask(99L, 1L, "질문", false)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("장인이 자신의 상품 문의에 답변한다")
    void answer_success() {
        ProductQuestion question = ProductQuestion.ask(1L, 99L, "질문", false);
        ReflectionTestUtils.setField(question, "id", 1L);
        ProductAnswer answer = ProductAnswer.reply(1L, 10L, "답변입니다");
        ReflectionTestUtils.setField(answer, "id", 1L);

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(answerRepository.existsByQuestionId(1L)).thenReturn(false);
        when(answerRepository.save(any())).thenReturn(answer);

        ProductQnaResponse.AnswerView result = service.answer(new ProductQnaCommand.Answer(1L, 10L, "답변입니다"));

        assertThat(result.content()).isEqualTo("답변입니다");
    }

    @Test
    @DisplayName("타 장인이 답변하면 ForbiddenException이 발생한다")
    void answer_forbiddenForOtherArtisan() {
        ProductQuestion question = ProductQuestion.ask(1L, 99L, "질문", false);
        ReflectionTestUtils.setField(question, "id", 1L);

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.answer(new ProductQnaCommand.Answer(1L, 99L, "답변")))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("이미 답변된 문의에 재답변하면 BusinessRuleViolationException이 발생한다")
    void answer_alreadyAnswered() {
        ProductQuestion question = ProductQuestion.ask(1L, 99L, "질문", false);
        ReflectionTestUtils.setField(question, "id", 1L);

        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(answerRepository.existsByQuestionId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.answer(new ProductQnaCommand.Answer(1L, 10L, "재답변")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("이미 답변");
    }
}
