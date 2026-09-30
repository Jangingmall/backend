package com.jangingmall.backend.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jangingmall.backend.content.domain.GenerationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GenerationResponseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    @DisplayName("FAILED 상태에서만 고정 대체 이미지 URL을 반환한다")
    void fallbackImageUrlOnlyWhenFailed() {
        assertThat(new GenerationResponse(1L, 10L, GenerationStatus.FAILED, NOW, NOW).fallbackImageUrl())
            .isEqualTo("http://test.webp");
        for (GenerationStatus status : GenerationStatus.values()) {
            if (status != GenerationStatus.FAILED) {
                assertThat(new GenerationResponse(1L, 10L, status, NOW, null).fallbackImageUrl()).isNull();
            }
        }
    }
}
