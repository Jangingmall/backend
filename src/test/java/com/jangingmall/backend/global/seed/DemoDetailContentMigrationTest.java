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

/** 큐레이션 상품 상세 소개(V22)가 약속한 내용을 DB 없이 SQL 파일과 저장소 이미지로 검증한다. */
class DemoDetailContentMigrationTest {

    private static final String RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/";

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V22__demo_detail_content.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("상세 블록이 가리키는 이미지는 모두 저장소에 있는 WebP 파일이다")
    void everyImageIsAnExistingWebp() throws Exception {
        Matcher matcher = Pattern.compile(Pattern.quote(RAW) + "([A-Za-z0-9_./-]+\\.webp)").matcher(sql);
        Set<String> paths = new HashSet<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertThat(paths).hasSizeGreaterThanOrEqualTo(8);
        for (String path : paths) {
            Path file = Path.of("docs", path);
            assertThat(file).as(file.toString()).exists();
            byte[] bytes = Files.readAllBytes(file);
            assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
            assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        }
    }

    @Test
    @DisplayName("상품 33개마다 본문 이미지 3장 이상과 소제목·본문 블록을 순서대로 심고 상세를 게시 상태로 둔다")
    void seedsRichBodyForEveryCuratedProduct() {
        Pattern row = Pattern.compile("(?m)^    \\('((?:[^']|'')*)', (\\d+), (\\d+), '(h2|p|img)', ");
        Matcher matcher = row.matcher(sql);
        java.util.Map<String, int[]> perProduct = new java.util.HashMap<>();
        while (matcher.find()) {
            int[] counts = perProduct.computeIfAbsent(matcher.group(1), key -> new int[2]);
            counts[0]++;
            if ("img".equals(matcher.group(4))) {
                counts[1]++;
            }
        }
        assertThat(perProduct).hasSize(33);
        perProduct.forEach((title, counts) -> {
            assertThat(counts[1]).as(title + " 본문 이미지").isGreaterThanOrEqualTo(3);
            assertThat(counts[0]).as(title + " 블록 수").isGreaterThanOrEqualTo(16);
        });
        assertThat(sql).contains("'PUBLISHED'").contains("ON CONFLICT (product_id) DO UPDATE");
    }
}
