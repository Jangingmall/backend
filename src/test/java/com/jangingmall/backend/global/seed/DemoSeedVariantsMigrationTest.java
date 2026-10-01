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

/** 기존 시드 상품 이미지 변형(V19)이 약속한 내용을 DB 없이 SQL 파일과 저장소 이미지로 검증한다. */
class DemoSeedVariantsMigrationTest {

    private static final String RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/";

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V19__demo_seed_product_image_variants.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("SQL 이 가리키는 모든 이미지는 저장소에 있는 WebP 파일(10MB 이하)이다")
    void everyImageIsAnExistingWebpWithinLimits() throws Exception {
        Matcher matcher = Pattern.compile(Pattern.quote(RAW) + "([A-Za-z0-9_./-]+\\.webp)").matcher(sql);
        Set<String> paths = new HashSet<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertThat(paths).hasSizeGreaterThan(200);
        for (String path : paths) {
            Path file = Path.of("docs", path);
            assertThat(file).as(file.toString()).exists();
            byte[] bytes = Files.readAllBytes(file);
            assertThat(bytes.length).as(file + " 크기").isBetween(100, 10 * 1024 * 1024);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        }
        assertThat(sql).doesNotContain("unsplash").doesNotContain("img.stg.midam.store");
    }

    @Test
    @DisplayName("대표 상품은 소분류 56종마다 하나씩 들어간다")
    void hasOneFlagshipPerSubcategory() {
        Matcher matcher = Pattern.compile("(?m)^    \\('[^']+', (\\d+), '" + Pattern.quote(RAW) + "demo-products/flagship/f\\d\\d\\.webp'").matcher(sql);
        Set<String> subcategories = new HashSet<>();
        int rows = 0;
        while (matcher.find()) {
            rows++;
            subcategories.add(matcher.group(1));
        }
        assertThat(rows).isEqualTo(56);
        assertThat(subcategories).hasSize(56);
    }

    @Test
    @DisplayName("아직 소분류 아이콘인 상품만 바꾸고, 이미 있는 상세 이미지는 덮어쓰지 않는다")
    void onlyTouchesProductsStillUsingTheIcon() {
        assertThat(sql.split("p.thumbnail_url LIKE '%/docs/seed-images/%'", -1).length - 1).isGreaterThanOrEqualTo(5);
        assertThat(sql).contains("ON CONFLICT DO NOTHING")
            .contains("NOT EXISTS (SELECT 1 FROM product_detail_image x WHERE x.product_id = p.product_id)")
            .doesNotContain("DELETE").doesNotContain("DROP TABLE");
    }
}
