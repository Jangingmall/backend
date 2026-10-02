package com.jangingmall.backend.product.application;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 저장된 상세 페이지 JSON(react_document)을 소비자 응답용으로 읽는다. 읽을 수 없는 JSON 은 없는 것으로 본다. */
final class DetailDocumentView {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private DetailDocumentView() {
    }

    static JsonNode parse(String reactDocument) {
        if (reactDocument == null || reactDocument.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(reactDocument);
            return root != null && root.has("root") ? root : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 문서 안의 article 노드(제목 h3 + 본문 p)를 특징으로 뽑는다. */
    static List<ProductResponse.FeatureView> features(JsonNode document) {
        List<ProductResponse.FeatureView> features = new ArrayList<>();
        if (document != null) {
            collect(document.path("root"), features);
        }
        return features;
    }

    private static void collect(JsonNode nodes, List<ProductResponse.FeatureView> features) {
        for (JsonNode node : nodes) {
            if ("article".equals(node.path("tag").asText(""))) {
                String title = firstText(node, "h3");
                String body = firstText(node, "p");
                if (!title.isBlank() && !body.isBlank()) {
                    features.add(new ProductResponse.FeatureView(title, body));
                }
            } else {
                collect(node.path("children"), features);
            }
        }
    }

    private static String firstText(JsonNode node, String tag) {
        for (JsonNode child : node.path("children")) {
            if (tag.equals(child.path("tag").asText(""))) {
                StringBuilder text = new StringBuilder();
                for (JsonNode leaf : child.path("children")) {
                    text.append(leaf.path("value").asText(""));
                }
                return text.toString();
            }
        }
        return "";
    }
}
