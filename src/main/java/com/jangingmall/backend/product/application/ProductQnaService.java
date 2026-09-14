package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductAnswer;
import com.jangingmall.backend.product.domain.ProductAnswerRepository;
import com.jangingmall.backend.product.domain.ProductErrorMessage;
import com.jangingmall.backend.product.domain.ProductQnaErrorMessage;
import com.jangingmall.backend.product.domain.ProductQuestion;
import com.jangingmall.backend.product.domain.ProductQuestionRepository;
import com.jangingmall.backend.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductQnaService {

    private final ProductRepository productRepository;
    private final ProductQuestionRepository questionRepository;
    private final ProductAnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public Page<ProductQnaResponse.QuestionView> findQuestions(Long productId, Long viewerId, Pageable pageable) {
        Product product = getProduct(productId);
        return questionRepository.findByProductId(productId, pageable)
            .map(question -> {
                ProductAnswer answer = answerRepository.findByQuestionId(question.getId()).orElse(null);
                return ProductQnaResponse.QuestionView.of(question, viewerId, product.getArtisanId(), answer);
            });
    }

    @Transactional
    public ProductQnaResponse.QuestionView ask(ProductQnaCommand.Ask command) {
        getProduct(command.productId());
        ProductQuestion question = ProductQuestion.ask(
            command.productId(),
            command.writerId(),
            command.content(),
            command.secret()
        );
        ProductQuestion saved = questionRepository.save(question);
        return ProductQnaResponse.QuestionView.of(saved, command.writerId(), null, null);
    }

    @Transactional
    public ProductQnaResponse.AnswerView answer(ProductQnaCommand.Answer command) {
        ProductQuestion question = getQuestion(command.questionId());
        Product product = getProduct(question.getProductId());

        if (!product.getArtisanId().equals(command.artisanId())) {
            throw new ForbiddenException(ProductQnaErrorMessage.ANSWER_FORBIDDEN.message());
        }
        if (answerRepository.existsByQuestionId(command.questionId())) {
            throw new BusinessRuleViolationException(ProductQnaErrorMessage.ALREADY_ANSWERED.message());
        }
        ProductAnswer saved = answerRepository.save(
            ProductAnswer.reply(command.questionId(), command.artisanId(), command.content())
        );
        return ProductQnaResponse.AnswerView.from(saved);
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new NotFoundException(ProductErrorMessage.NOT_FOUND.message()));
    }

    private ProductQuestion getQuestion(Long questionId) {
        return questionRepository.findById(questionId)
            .orElseThrow(() -> new NotFoundException(ProductQnaErrorMessage.QUESTION_NOT_FOUND.message()));
    }
}
