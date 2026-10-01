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

/** 실사 이미지 연결(V20)이 약속한 내용을 DB 없이 SQL 파일과 저장소 이미지로 검증한다. */
class DemoPhotorealMigrationTest {

    private static final String RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/";

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V20__demo_photoreal_images.sql")
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
        assertThat(paths).hasSizeGreaterThan(300);
        for (String path : paths) {
            assertThat(path).startsWith("demo-products/photoreal/staging/");
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
    @DisplayName("큐레이션 33개는 모두 대표(A)·상세(B·C) 실사로 연결된다")
    void curatedProductsAreFullyPhotoreal() {
        String curated = sql.substring(sql.indexOf("INSERT INTO demo_photoreal_c"), sql.indexOf("CREATE TEMP TABLE demo_photoreal_s"));
        Matcher matcher = Pattern.compile("(?m)^    \\('[^']+', \\d+, '[^']+_A\\.webp', '[^']+_B\\.webp', '[^']+_C\\.webp'\\)").matcher(curated);
        int rows = 0;
        while (matcher.find()) {
            rows++;
        }
        assertThat(rows).isEqualTo(33);
    }

    @Test
    @DisplayName("시드 상품(저장소 docs 이미지)만 바꾸고 사용자가 만든 상품은 건드리지 않는다")
    void touchesOnlySeededProducts() {
        assertThat(sql).contains("ON COMMIT DROP")
            .contains("p.thumbnail_url LIKE '%/Jangingmall/backend/%/docs/%'")
            .contains("ON CONFLICT (product_id, display_order) DO UPDATE");
        assertThat(sql.split("UPDATE product p SET thumbnail_url", -1).length - 1).isEqualTo(2);
    }
}
