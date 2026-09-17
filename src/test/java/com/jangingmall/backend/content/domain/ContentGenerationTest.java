package com.jangingmall.backend.content.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentGenerationTest {

    private static final String REACT_DOCUMENT_JSON =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}";

    @Test
    @DisplayName("생성하면 PROCESSING 상태로 초기화된다")
    void create() {
        ContentGeneration generation = ContentGeneration.create(1L, "img1,img2", "청자 다완", "손으로 빚음", "물 닦기");

        assertThat(generation.getProductId()).isEqualTo(1L);
        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
        assertThat(generation.getRequestedAt()).isNotNull();
        assertThat(generation.getCompletedAt()).isNull();
        assertThat(generation.getReactDocument()).isNull();
    }

    @Test
    @DisplayName("complete() 호출 시 COMPLETED 상태로 전환되고 reactDocument와 completedAt이 설정된다")
    void complete() {
        ContentGeneration generation = ContentGeneration.create(1L, "img1", "상품명", "과정", "관리");

        generation.complete(REACT_DOCUMENT_JSON, "idem-key");

        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(generation.getReactDocument()).isEqualTo(REACT_DOCUMENT_JSON);
        assertThat(generation.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("fail() 호출 시 FAILED 상태로 전환되고 completedAt이 설정된다")
    void fail() {
        ContentGeneration generation = ContentGeneration.create(1L, "img1", "상품명", "과정", "관리");

        generation.fail();

        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(generation.getCompletedAt()).isNotNull();
    }
}
