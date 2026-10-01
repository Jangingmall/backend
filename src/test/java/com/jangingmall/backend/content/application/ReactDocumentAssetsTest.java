package com.jangingmall.backend.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ReactDocumentAssetsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DOCUMENT = "{\"root\":[{\"tag\":\"section\",\"children\":["
        + "{\"tag\":\"img\",\"props\":{\"imageId\":\"hero\"}},"
        + "{\"tag\":\"img\",\"props\":{\"imageId\":\"01JUPLOADEDIMAGE\"}}]}]}";

    private JsonNode image(String json, int index) throws Exception {
        return objectMapper.readTree(json).get("root").get(0).get("children").get(index).get("props");
    }

    @Test
    @DisplayName("저장할 때 S3 키(assetKey)와 공개 주소(src)를 채우고, 모르는 imageId 는 건드리지 않는다")
    void attachesKeyAndUrl() throws Exception {
        String result = ReactDocumentAssets.attach(objectMapper, DOCUMENT,
            Map.of("hero", "images/product/ai-generated/1/photo-hero.webp"), "https://img.stg.midam.store/");

        assertThat(image(result, 0).get("assetKey").asText()).isEqualTo("images/product/ai-generated/1/photo-hero.webp");
        assertThat(image(result, 0).get("src").asText())
            .isEqualTo("https://img.stg.midam.store/images/product/ai-generated/1/photo-hero.webp");
        assertThat(image(result, 0).get("imageId").asText()).isEqualTo("hero");
        assertThat(image(result, 1).has("src")).isFalse();
    }

    @Test
    @DisplayName("조회할 때 저장된 assetKey 로 현재 기본 주소 기준 src 를 다시 만든다")
    void refreshesSrcWithCurrentBaseUrl() throws Exception {
        String stored = ReactDocumentAssets.attach(objectMapper, DOCUMENT,
            Map.of("hero", "images/product/ai-generated/1/photo-hero.webp"), "https://img.stg.midam.store");

        String refreshed = ReactDocumentAssets.refresh(objectMapper, stored, "https://img.midam.store");

        assertThat(image(refreshed, 0).get("src").asText())
            .isEqualTo("https://img.midam.store/images/product/ai-generated/1/photo-hero.webp");
        assertThat(ReactDocumentAssets.refresh(objectMapper, stored, "https://img.stg.midam.store")).isSameAs(stored);
    }

    @Test
    @DisplayName("assetKey 가 없는 문서나 기본 주소가 없는 환경은 원문을 그대로 돌려준다")
    void leavesDocumentAloneWhenNothingToDo() {
        assertThat(ReactDocumentAssets.refresh(objectMapper, DOCUMENT, "https://img.stg.midam.store")).isSameAs(DOCUMENT);
        assertThat(ReactDocumentAssets.attach(objectMapper, DOCUMENT, Map.of("hero", "k"), "")).isSameAs(DOCUMENT);
        assertThat(ReactDocumentAssets.refresh(objectMapper, null, "https://img.stg.midam.store")).isNull();
    }
}
