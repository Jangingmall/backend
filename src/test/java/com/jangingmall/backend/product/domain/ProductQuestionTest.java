package com.jangingmall.backend.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductQuestionTest {

    @Test
    @DisplayName("공개 문의는 누구나 내용을 볼 수 있다")
    void visibleContent_public() {
        ProductQuestion question = ProductQuestion.ask(1L, 10L, "공개 질문입니다", false);

        assertThat(question.visibleContent(99L, 20L)).isEqualTo("공개 질문입니다");
    }

    @Test
    @DisplayName("비공개 문의는 작성자가 내용을 볼 수 있다")
    void visibleContent_secret_writer() {
        ProductQuestion question = ProductQuestion.ask(1L, 10L, "비공개 질문입니다", true);

        assertThat(question.visibleContent(10L, 20L)).isEqualTo("비공개 질문입니다");
    }

    @Test
    @DisplayName("비공개 문의는 장인이 내용을 볼 수 있다")
    void visibleContent_secret_artisan() {
        ProductQuestion question = ProductQuestion.ask(1L, 10L, "비공개 질문입니다", true);

        assertThat(question.visibleContent(20L, 20L)).isEqualTo("비공개 질문입니다");
    }

    @Test
    @DisplayName("비공개 문의는 제3자에게 마스킹된다")
    void visibleContent_secret_other() {
        ProductQuestion question = ProductQuestion.ask(1L, 10L, "비공개 질문입니다", true);

        assertThat(question.visibleContent(99L, 20L)).isEqualTo("비공개 문의입니다.");
    }
}
