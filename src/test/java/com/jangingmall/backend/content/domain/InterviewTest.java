package com.jangingmall.backend.content.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewTest {

    @Test
    @DisplayName("취재 데이터를 생성하면 productId와 필드 값이 저장된다")
    void create() {
        Interview interview = Interview.create(1L, "손으로 직접 빚음", "청자", "청자기법", "300년 가문의 전통");

        assertThat(interview.getProductId()).isEqualTo(1L);
        assertThat(interview.getProcess()).isEqualTo("손으로 직접 빚음");
        assertThat(interview.getMaterials()).isEqualTo("청자");
        assertThat(interview.getTechnique()).isEqualTo("청자기법");
        assertThat(interview.getStory()).isEqualTo("300년 가문의 전통");
    }

    @Test
    @DisplayName("update 시 null 필드는 기존 값을 유지한다")
    void updateKeepsNullFields() {
        Interview interview = Interview.create(1L, "원래 과정", "원래 소재", "원래 기법", "원래 스토리");

        interview.update(null, "새 소재", null, null);

        assertThat(interview.getProcess()).isEqualTo("원래 과정");
        assertThat(interview.getMaterials()).isEqualTo("새 소재");
        assertThat(interview.getTechnique()).isEqualTo("원래 기법");
        assertThat(interview.getStory()).isEqualTo("원래 스토리");
    }

    @Test
    @DisplayName("update 시 값이 있는 필드는 모두 교체된다")
    void updateAllFields() {
        Interview interview = Interview.create(1L, "원래 과정", "원래 소재", "원래 기법", "원래 스토리");

        interview.update("새 과정", "새 소재", "새 기법", "새 스토리");

        assertThat(interview.getProcess()).isEqualTo("새 과정");
        assertThat(interview.getMaterials()).isEqualTo("새 소재");
        assertThat(interview.getTechnique()).isEqualTo("새 기법");
        assertThat(interview.getStory()).isEqualTo("새 스토리");
    }
}
