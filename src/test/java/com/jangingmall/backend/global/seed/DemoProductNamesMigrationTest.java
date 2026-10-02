package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 시드 상품 이름·설명 정리(V21)가 약속한 내용을 DB 없이 SQL 파일로 검증한다. */
class DemoProductNamesMigrationTest {

    private static final Pattern ROW =
        Pattern.compile("(?m)^    \\('((?:[^']|'')*)', (\\d+), '((?:[^']|'')*)', '((?:[^']|'')*)'\\)[,;]$");

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V21__demo_product_names_descriptions.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("시드 상품 729개를 모두 다시 쓰고, 새 이름에는 숫자가 없고 자리 채우기 이름이 남지 않는다")
    void everySeedProductGetsACleanNameAndDescription() {
        Matcher matcher = ROW.matcher(sql);
        int rows = 0;
        Set<String> keys = new HashSet<>();
        while (matcher.find()) {
            rows++;
            String title = matcher.group(3);
            String description = matcher.group(4);
            assertThat(title).as(title).doesNotContainPattern("\\d").doesNotStartWith("전통 문양");
            assertThat(description).as(title).hasSizeGreaterThan(30).doesNotContain("현대식");
            assertThat(keys.add(matcher.group(2) + "/" + title)).as("같은 소분류 안에서 이름이 겹치지 않는다: " + title).isTrue();
        }
        assertThat(rows).isEqualTo(729);
    }

    @Test
    @DisplayName("시드 상품(저장소 docs 이미지)만 바꾸고 큐레이션·사용자 상품은 건드리지 않는다")
    void touchesOnlySeededProducts() {
        assertThat(sql).contains("ON COMMIT DROP")
            .contains("p.title = r.old_title AND p.subcategory_id = r.subcategory_id")
            .contains("p.thumbnail_url LIKE '%/Jangingmall/backend/%/docs/%'");
    }
}
