package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 시연 시드(V16)가 약속한 내용을 DB 없이 SQL 파일만으로 검증한다. */
class DemoSeedMigrationTest {

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource(
            "db/migration/V16__demo_consumer_seed_images_gift_themes_artisans.sql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("소분류 1~56 모두의 일러스트(WebP)가 저장소에 있고, 업로드 규격(WebP·10MB 이하)을 지킨다")
    void everySubcategoryHasAWebpIllustrationWithinLimits() throws Exception {
        for (int id = 1; id <= 56; id++) {
            Path file = Path.of("docs/seed-images", String.format("sub-%02d.webp", id));
            assertThat(file).as(file.toString()).exists();
            byte[] bytes = Files.readAllBytes(file);
            assertThat(bytes.length).as(file + " 크기").isBetween(100, 10 * 1024 * 1024);
            // WebP 파일은 RIFF....WEBP 로 시작한다
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        }
        assertThat(sql).contains("p.subcategory_id BETWEEN 1 AND 56");
    }

    @Test
    @DisplayName("대표 이미지는 서버(S3/CDN)가 아닌 저장소 외부 링크(.webp)로 연결하고 다른 외부 사이트를 가리키지 않는다")
    void thumbnailPointsAtRepositoryFiles() {
        assertThat(sql).contains("https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/seed-images/sub-")
            .contains("|| '.webp'");
        assertThat(sql).doesNotContain("img.stg.midam.store").doesNotContain("unsplash");
    }

    @Test
    @DisplayName("직접 업로드한 이미지가 있는 상품은 건드리지 않고, 선물 테마는 중복 없이 넣는다")
    void protectsUploadedProductsAndAvoidsDuplicates() {
        assertThat(sql).contains("NOT EXISTS (SELECT 1 FROM product_image pi WHERE pi.product_id = p.product_id)");
        assertThat(sql).contains("NOT EXISTS (")
            .contains("g.gift_theme = t.gift_theme");
    }

    @Test
    @DisplayName("선물 테마 8종(FE 목록)이 모두 들어 있다")
    void coversAllEightGiftThemes() {
        for (String theme : new String[] {"HOUSEWARMING", "BIRTHDAY_60TH", "WEDDING", "BOSS", "PARENTS", "FRIEND",
            "PROMOTION", "CORPORATE"}) {
            assertThat(sql).contains("('" + theme + "',");
        }
    }

    @Test
    @DisplayName("\"더미 수량 보완용 가상 …\" 같은 안내 문구와 한 단어짜리 밀린 설명을 문장으로 바꾼다")
    void rewritesDummyAndShiftedDescriptions() {
        assertThat(sql).contains("p.description LIKE '%더미%'").contains("p.description LIKE '%가상%'")
            .contains("length(p.description) < 8");
        assertThat(sql).contains("mode() WITHIN GROUP (ORDER BY material)");
    }
}
