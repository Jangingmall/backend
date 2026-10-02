package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 시연 디자인 상품(V23)이 약속한 내용을 DB 없이 SQL 파일과 저장소 이미지로 검증한다. */
class DemoDesignProductsMigrationTest {

    private static final String RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/";

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V23__demo_design_products.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("홈 디자인 상품 13개를 넣고 이미지는 모두 저장소의 WebP 파일이다")
    void insertsThirteenProductsWithExistingImages() throws Exception {
        long rows = sql.lines().filter(line -> line.matches("^    \\(\\d+, '.*, NOW\\(\\).*\\)[,]?$")).count();
        assertThat(rows).isEqualTo(13);
        Matcher matcher = Pattern.compile(Pattern.quote(RAW) + "([A-Za-z0-9_./-]+\\.webp)").matcher(sql);
        Set<String> paths = new HashSet<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertThat(paths).hasSizeGreaterThanOrEqualTo(10);
        for (String path : paths) {
            Path file = Path.of("docs", path);
            assertThat(file).as(file.toString()).exists();
            byte[] bytes = Files.readAllBytes(file);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        }
    }

    @Test
    @DisplayName("이미 있으면 다시 넣지 않고, 순서(id)를 베스트→기획전→신상품 노출에 맞춘다")
    void isIdempotentAndOrdered() {
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM product e WHERE e.title = v.title AND e.artisan_id = v.artisan_id)")
            .contains("ORDER BY v.ord")
            .contains("ON CONFLICT DO NOTHING");
    }
}
