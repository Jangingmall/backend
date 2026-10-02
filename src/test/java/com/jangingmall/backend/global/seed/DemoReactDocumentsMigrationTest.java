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

/** 시연 상품의 AI 상세 페이지 JSON(V24)이 상세페이지 JSON 계약(schemaVersion 2.0, 허용 태그, 이벤트 금지)을 지키는지 SQL 문자열로 검증한다. */
class DemoReactDocumentsMigrationTest {

    private static final Set<String> ALLOWED_TAGS = Set.of("section", "article", "div", "h2", "h3", "h4", "p", "span", "strong", "em",
        "ul", "ol", "li", "figure", "figcaption", "img", "video", "a", "table", "caption", "thead", "tbody", "tr", "th", "td");

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        sql = new String(new ClassPathResource("db/migration/V24__demo_react_documents.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("문서 4개가 schemaVersion 2.0·캔버스 774 로 저장되고 이벤트·스크립트 문자열이 없다")
    void documentsFollowTheContract() {
        assertThat(sql.split("\"schemaVersion\":\"2.0\"", -1).length - 1).isEqualTo(4);
        assertThat(sql.split("\"canvasWidth\":774", -1).length - 1).isEqualTo(4);
        assertThat(sql).doesNotContain("onClick").doesNotContain("onLoad").doesNotContain("javascript:")
            .doesNotContain("dangerouslySetInnerHTML").doesNotContain("<script").doesNotContain("className");
    }

    @Test
    @DisplayName("모든 태그는 허용 목록 안에 있고, 이미지 노드는 imageId·alt 를 가진다")
    void usesOnlyAllowedTagsAndImagesCarryImageIds() {
        Matcher matcher = Pattern.compile("\"tag\":\"([a-z0-9]+)\"").matcher(sql);
        Set<String> tags = new HashSet<>();
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        assertThat(ALLOWED_TAGS).containsAll(tags);
        long images = sql.split("\"tag\":\"img\"", -1).length - 1;
        long imageIds = sql.split("\"imageId\":\"body_", -1).length - 1;
        assertThat(images).isEqualTo(32);
        assertThat(imageIds).isEqualTo(images);
    }
}
