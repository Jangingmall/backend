package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 시연용 큐레이션 상품(V17)이 약속한 내용을 DB 없이 SQL 파일과 저장소 이미지로 검증한다. */
class DemoCuratedProductsMigrationTest {

    private static final String RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/";
    private static final Set<String> GIFT_THEMES = Set.of("HOUSEWARMING", "BIRTHDAY_60TH", "WEDDING", "BOSS", "PARENTS",
        "FRIEND", "PROMOTION", "CORPORATE");

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V17__demo_curated_products.sql").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("상품 33개가 이름 중복 없이 들어가고, 이미 같은 이름이 있으면 다시 넣지 않는다")
    void insertsThirtyThreeUniqueProducts() {
        Matcher matcher = Pattern.compile("(?m)^    \\('([^']+)', (\\d+), (\\d+), '").matcher(sql);
        Set<String> names = new HashSet<>();
        int rows = 0;
        while (matcher.find()) {
            rows++;
            names.add(matcher.group(1));
        }
        assertThat(rows).isEqualTo(33);
        assertThat(names).hasSize(33);
        assertThat(sql).contains("WHERE NOT EXISTS (SELECT 1 FROM product e WHERE e.title = v.title AND e.artisan_id = v.artisan_id)");
    }

    @Test
    @DisplayName("대표 이미지 33개와 상세 이미지 69개는 모두 저장소에 있는 WebP 파일(10MB 이하)을 가리킨다")
    void everyThumbnailIsAnExistingWebpWithinLimits() throws Exception {
        Matcher matcher = Pattern.compile(Pattern.quote(RAW) + "([A-Za-z0-9_./-]+\\.webp)").matcher(sql);
        Set<String> paths = new HashSet<>();
        int total = 0;
        while (matcher.find()) {
            total++;
            paths.add(matcher.group(1));
        }
        assertThat(total).isEqualTo(33 + 69);
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
    @DisplayName("상세 갤러리용 이미지 테이블을 만들고 상품마다 상세 이미지(특징·상품 정보 카드)를 순서대로 넣는다")
    void insertsTwoDetailImagesPerProduct() {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS product_detail_image")
            .contains("INSERT INTO product_detail_image (product_id, display_order, image_url)");
        Matcher matcher = Pattern.compile("(?m)^    \\('[^']+', ([01]), '" + Pattern.quote(RAW)).matcher(sql);
        int first = 0;
        int second = 0;
        while (matcher.find()) {
            if (matcher.group(1).equals("0")) {
                first++;
            } else {
                second++;
            }
        }
        assertThat(first).isEqualTo(33);
        assertThat(second).isEqualTo(33);
    }

    @Test
    @DisplayName("선물 테마는 FE의 8종 안에서만 쓴다")
    void usesOnlyKnownGiftThemes() {
        Matcher matcher = Pattern.compile("(?m)^    \\('[^']+', '([A-Z_0-9]+)'\\)").matcher(sql);
        List<String> themes = new java.util.ArrayList<>();
        while (matcher.find()) {
            themes.add(matcher.group(1));
        }
        assertThat(themes).isNotEmpty().allMatch(GIFT_THEMES::contains);
    }
}
