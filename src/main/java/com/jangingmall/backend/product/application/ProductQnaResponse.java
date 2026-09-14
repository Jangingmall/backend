package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.ProductAnswer;
import com.jangingmall.backend.product.domain.ProductQuestion;

import java.time.LocalDateTime;

public sealed interface ProductQnaResponse {

    record QuestionView(
        Long questionId,
        Long productId,
        Long writerId,
        String content,
        boolean secret,
        LocalDateTime createdAt,
        AnswerView answer
    ) implements ProductQnaResponse {

        public static QuestionView of(ProductQuestion question, Long viewerId, Long artisanId, ProductAnswer answer) {
            return new QuestionView(
                question.getId(),
                question.getProductId(),
                question.getWriterId(),
                question.visibleContent(viewerId, artisanId),
                question.isSecret(),
                question.getCreatedAt(),
                answer == null ? null : AnswerView.from(answer)
            );
        }
    }

    record AnswerView(
        Long answerId,
        Long artisanId,
        String content,
        LocalDateTime answeredAt
    ) implements ProductQnaResponse {

        public static AnswerView from(ProductAnswer answer) {
            return new AnswerView(
                answer.getId(),
                answer.getArtisanId(),
                answer.getContent(),
                answer.getAnsweredAt()
            );
        }
    }
}
