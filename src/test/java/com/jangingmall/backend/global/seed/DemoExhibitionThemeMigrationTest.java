package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 기획전 상품 4개에 EXHIBITION 테마를 붙이는 V27 을 SQL 문자열로 검증한다. */
class DemoExhibitionThemeMigrationTest {

    @Test
    @DisplayName("기획전 상품 4개에만 EXHIBITION 테마를 중복 없이 붙인다")
    void tagsFourExhibitionProducts() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V27__demo_exhibition_theme.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("'EXHIBITION'").contains("NOT EXISTS")
            .contains("'대나무 조명'").contains("'백잔'").contains("'자연염 테이블 러너'").contains("'산수화 대형 부채'");
    }
}
