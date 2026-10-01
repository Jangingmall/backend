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
    @DisplayName("소분류 1~56 모두에 STG 이미지 서버의 서로 다른 이미지 ID 가 하나씩 짝지어진다")
    void everySubcategoryHasItsOwnCdnImage() {
        Matcher matcher = Pattern.compile("\\((\\d+), '([0-9A-Z]{26})'\\)").matcher(sql);
        Set<Integer> subcategories = new HashSet<>();
        Set<String> imageIds = new HashSet<>();
        while (matcher.find()) {
            subcategories.add(Integer.parseInt(matcher.group(1)));
            imageIds.add(matcher.group(2));
        }
        assertThat(subcategories).containsExactlyInAnyOrderElementsOf(
            java.util.stream.IntStream.rangeClosed(1, 56).boxed().toList());
        assertThat(imageIds).hasSize(56);
    }

    @Test
    @DisplayName("대표 이미지는 이미지 서버(img.stg.midam.store) 1280w 주소로 만들고 외부 사이트를 가리키지 않는다")
    void thumbnailPointsAtImageServer() {
        assertThat(sql).contains("https://img.stg.midam.store/images/product/63/' || m.image_id || '/1280w.webp");
        assertThat(sql).doesNotContain("raw.githubusercontent.com").doesNotContain("unsplash");
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
}
