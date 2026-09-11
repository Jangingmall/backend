package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 주문 상품에 저장하는 옵션/장바구니 스냅샷의 단일 인코더다. */
final class CheckoutSnapshot {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private CheckoutSnapshot() {}

    static String encode(Long cartItemId, List<CheckoutCatalog.OptionSelection> options,
                         List<CheckoutCatalog.TextInput> textInputs) {
        try {
            return JSON.writeValueAsString(Map.of(
                "cartItemId", cartItemId,
                "selectedOptions", options,
                "textInputs", textInputs
            ));
        } catch (Exception exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
    }

    static Long cartItemId(String snapshot) {
        Object value = read(snapshot).get("cartItemId");
        return value instanceof Number number ? number.longValue() : null;
    }

    static List<Long> choiceIds(String snapshot) {
        Object selections = read(snapshot).get("selectedOptions");
        if (!(selections instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(selection -> selection.get("choiceId"))
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .map(Number::longValue)
            .toList();
    }

    private static Map<String, Object> read(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(snapshot, MAP_TYPE);
        } catch (Exception exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
