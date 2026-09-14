package com.jangingmall.backend.content.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentBlockTest {

    private Content dummyContent() {
        Content c = Content.create(1L);
        ReflectionTestUtils.setField(c, "id", 1L);
        return c;
    }

    @Test
    @DisplayName("updateText() — h2/p 블록에서 텍스트를 수정한다")
    void updateTextSuccess() {
        ContentBlock block = ContentBlock.create(dummyContent(), (short) 1, BlockTag.p, null, null, "원래 텍스트");
        block.updateText("새 텍스트");
        assertThat(block.getText()).isEqualTo("새 텍스트");
    }

    @Test
    @DisplayName("updateText() — img 블록에 텍스트 수정 시 BusinessRuleViolationException이 발생한다")
    void updateTextOnImageBlock() {
        ContentBlock block = ContentBlock.create(dummyContent(), (short) 1, BlockTag.img, "img001", null, null);
        assertThatThrownBy(() -> block.updateText("텍스트"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("updateImage() — img 블록에서 이미지 ID를 수정한다")
    void updateImageSuccess() {
        ContentBlock block = ContentBlock.create(dummyContent(), (short) 2, BlockTag.img, "oldImgId", null, null);
        block.updateImage("newImgId");
        assertThat(block.getImageId()).isEqualTo("newImgId");
    }

    @Test
    @DisplayName("updateImage() — p 블록에 이미지 수정 시 BusinessRuleViolationException이 발생한다")
    void updateImageOnTextBlock() {
        ContentBlock block = ContentBlock.create(dummyContent(), (short) 1, BlockTag.p, null, null, "텍스트");
        assertThatThrownBy(() -> block.updateImage("imgId"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("replaceWith() — 태그·내용을 한 번에 교체한다")
    void replaceWith() {
        ContentBlock block = ContentBlock.create(dummyContent(), (short) 1, BlockTag.p, null, null, "원래");
        block.replaceWith(BlockTag.h2, "새 소제목", null, null);
        assertThat(block.getTag()).isEqualTo(BlockTag.h2);
        assertThat(block.getText()).isEqualTo("새 소제목");
    }
}
