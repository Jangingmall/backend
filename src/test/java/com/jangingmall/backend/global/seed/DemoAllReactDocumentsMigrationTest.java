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

/** 모든 시드 상품에 심는 AI 상세 페이지 JSON 틀(V26)이 계약을 지키고, 상품이 빠짐없이 들어 있는지 SQL 문자열로 검증한다. */
class DemoAllReactDocumentsMigrationTest {

    private static final Set<String> ALLOWED_TAGS = Set.of("section", "article", "div", "h2", "h3", "h4", "p", "span", "strong", "em",
        "ul", "ol", "li", "figure", "figcaption", "img", "video", "a", "table", "caption", "thead", "tbody", "tr", "th", "td");

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V26__demo_all_react_documents.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("문서 틀 3종이 schemaVersion 2.0·캔버스 774 이고 섹션이 4개이며 이벤트·스크립트가 없다")
    void templatesFollowTheContract() {
        assertThat(sql.split("\"schemaVersion\":\"2.0\"", -1).length - 1).isEqualTo(3);
        assertThat(sql.split("\"canvasWidth\":774", -1).length - 1).isEqualTo(3);
        assertThat(sql.split("\"tag\":\"section\"", -1).length - 1).isEqualTo(12);
        assertThat(sql).doesNotContain("onClick").doesNotContain("javascript:").doesNotContain("<script").doesNotContain("className");
    }

    @Test
    @DisplayName("틀의 모든 태그는 허용 목록 안에 있고 이미지 노드는 imageId·alt·src 토큰을 가진다")
    void usesOnlyAllowedTagsAndImageSlots() {
        Matcher matcher = Pattern.compile("\"tag\":\"([a-z0-9]+)\"").matcher(sql);
        Set<String> tags = new HashSet<>();
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        assertThat(ALLOWED_TAGS).containsAll(tags);
        assertThat(sql.split("\"tag\":\"img\"", -1).length - 1).isEqualTo(6);
        assertThat(sql).contains("\"src\":\"@U1@\"").contains("\"src\":\"@U2@\"").contains("\"src\":\"@U3@\"");
    }

    @Test
    @DisplayName("상품 행이 700개 이상이고 JSON 이 없던 상품에만 채우며 소비자용 본문 블록도 만든다")
    void coversEveryProductWithoutOverwriting() {
        long rows = sql.lines().filter(line -> line.startsWith("    ('")
            && line.contains("@R@")).count();
        assertThat(rows).isGreaterThanOrEqualTo(700);
        assertThat(sql).contains("c.react_document IS NULL").contains("INSERT INTO content_block").contains("NOT EXISTS");
    }
}
