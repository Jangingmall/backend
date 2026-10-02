package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 홈 베스트 5·기획전 4 상품의 AI 상세 JSON(V30)이 계약을 지키고 사진 슬롯 3장씩을 쓰는지 SQL 문자열로 검증한다. */
class DemoHomeReactDocumentsMigrationTest {

    private static final Set<String> ALLOWED_TAGS = Set.of("section", "article", "div", "h2", "h3", "h4", "p", "span", "strong", "em",
        "ul", "ol", "li", "figure", "figcaption", "img", "video", "a", "table", "caption", "thead", "tbody", "tr", "th", "td");

    @Test
    @DisplayName("문서 9개가 schemaVersion 2.0·캔버스 774, 섹션 7개, 허용 태그만 쓰고 이미지는 슬롯마다 imageId 를 가진다")
    void documentsFollowTheContract() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V30__demo_home_react_documents.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql.split("\"schemaVersion\":\"2.0\"", -1).length - 1).isEqualTo(9);
        assertThat(sql.split("\"canvasWidth\":774", -1).length - 1).isEqualTo(9);
        assertThat(sql.split("\"tag\":\"section\"", -1).length - 1).isEqualTo(63);
        assertThat(sql).doesNotContain("onClick").doesNotContain("javascript:").doesNotContain("<script");
        Matcher matcher = Pattern.compile("\"tag\":\"([a-z0-9]+)\"").matcher(sql);
        Set<String> tags = new HashSet<>();
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        assertThat(ALLOWED_TAGS).containsAll(tags);
        long images = sql.split("\"tag\":\"img\"", -1).length - 1;
        assertThat(images).isEqualTo(54);
        assertThat(sql.split("\"imageId\":\"home_", -1).length - 1).isEqualTo(images);
    }
}
