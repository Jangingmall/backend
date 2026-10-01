package com.jangingmall.backend.content.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 가 만든 react 문서의 img 노드는 photo_id(props.imageId)만 갖고 있어 화면이 그림을 찾지 못한다.
 * 저장할 때 S3 키를 props.assetKey 로 함께 남기고(도메인과 무관한 상대 경로), 이미지 기본 주소(image.base-url)와 합친
 * 공개 주소를 props.src 에 채운다. 조회할 때 현재 기본 주소로 src 를 다시 만들어 도메인이 바뀌어도 맞게 한다.
 */
@Slf4j
final class ReactDocumentAssets {

    private ReactDocumentAssets() {}

    /** 올린 사진의 {photo_id → S3 키}로 img 노드에 assetKey·src 를 채운다. 바꿀 것이 없으면 원문을 그대로 돌려준다. */
    static String attach(ObjectMapper objectMapper, String json, Map<String, String> keysByPhotoId, String baseUrl) {
        if (blank(json) || keysByPhotoId == null || keysByPhotoId.isEmpty() || blank(baseUrl)) {
            return json;
        }
        return transform(objectMapper, json, node -> {
            Object ref = reference(node);
            String key = ref == null ? null : keysByPhotoId.get(String.valueOf(ref));
            if (key == null) {
                return false;
            }
            Map<String, Object> props = props(node);
            props.put("assetKey", key);
            props.put("src", join(baseUrl, key));
            return true;
        });
    }

    /**
     * assetKey 가 없는 예전 문서용. src·assetKey 가 없고 업로드 이미지 ID(대문자 ULID 26자)가 아닌 img 노드의 imageId(photo_id)를
     * resolver 로 S3 키에 맞춰 assetKey·src 를 채운다. resolver 가 null 을 주면 그 노드는 그대로 둔다.
     */
    static String attachLegacy(ObjectMapper objectMapper, String json, java.util.function.Function<String, String> resolver,
                               String baseUrl) {
        if (blank(json) || blank(baseUrl) || resolver == null) {
            return json;
        }
        return transform(objectMapper, json, node -> {
            Object props = node.get("props");
            if (props instanceof Map<?, ?> map && (map.get("assetKey") != null || map.get("src") != null)) {
                return false;
            }
            Object ref = reference(node);
            if (ref == null || String.valueOf(ref).matches("[0-9A-Z]{26}")) {
                return false;
            }
            String key = resolver.apply(String.valueOf(ref));
            if (key == null) {
                return false;
            }
            Map<String, Object> target = props(node);
            target.put("assetKey", key);
            target.put("src", join(baseUrl, key));
            return true;
        });
    }

    /** 이 문서에 assetKey·src 가 없는 사진용 img 노드가 있는지(예전 문서인지) 대략 확인한다. */
    static boolean mayNeedLegacyResolution(String json) {
        return !blank(json) && json.contains("\"img\"");
    }

    /** 저장된 assetKey 로 src 를 현재 기본 주소 기준으로 다시 만든다. assetKey 가 없는 노드는 건드리지 않는다. */
    static String refresh(ObjectMapper objectMapper, String json, String baseUrl) {
        if (blank(json) || blank(baseUrl) || !json.contains("assetKey")) {
            return json;
        }
        return transform(objectMapper, json, node -> {
            Object props = node.get("props");
            if (!(props instanceof Map<?, ?> map) || map.get("assetKey") == null) {
                return false;
            }
            String src = join(baseUrl, String.valueOf(map.get("assetKey")));
            if (src.equals(map.get("src"))) {
                return false;
            }
            props(node).put("src", src);
            return true;
        });
    }

    private interface NodeEdit {
        boolean apply(Map<String, Object> imgNode);
    }

    private static String transform(ObjectMapper objectMapper, String json, NodeEdit edit) {
        try {
            Object document = objectMapper.readValue(json, Object.class);
            return walk(document, edit) ? objectMapper.writeValueAsString(document) : json;
        } catch (Exception exception) {
            log.warn("react 문서의 이미지 주소를 처리하지 못해 원문을 그대로 사용합니다", exception);
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean walk(Object node, NodeEdit edit) {
        boolean changed = false;
        if (node instanceof List<?> list) {
            for (Object child : list) {
                changed |= walk(child, edit);
            }
        } else if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if ("img".equals(map.get("tag"))) {
                changed |= edit.apply(map);
            }
            for (Object child : map.values()) {
                changed |= walk(child, edit);
            }
        }
        return changed;
    }

    private static Object reference(Map<String, Object> node) {
        Object props = node.get("props");
        if (props instanceof Map<?, ?> map && map.get("imageId") != null) {
            return map.get("imageId");
        }
        return node.get("imageId");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> props(Map<String, Object> node) {
        Object props = node.get("props");
        if (props instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        node.put("props", created);
        return created;
    }

    private static String join(String baseUrl, String key) {
        return baseUrl.replaceAll("/+$", "") + "/" + key.replaceAll("^/+", "");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
