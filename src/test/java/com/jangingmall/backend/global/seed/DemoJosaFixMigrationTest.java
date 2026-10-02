package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DemoJosaFixMigrationTest {

    @Test
    @DisplayName("받침 없는 품목만 '이라'를 '라'로 고치고, 받침 있는 품목은 건드리지 않는다")
    void fixesOnlyItemsWithoutFinalConsonant() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V31__fix_josa_ira.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("('노리개이라 사진과', '노리개라 사진과')").contains("('스카프이라 사진과', '스카프라 사진과')");
        assertThat(sql).doesNotContain("찻잔이라 사진과'").doesNotContain("('그릇이라");
        assertThat(sql).contains("UPDATE content_block").contains("UPDATE content c SET react_document");
    }
}
