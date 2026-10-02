package com.jangingmall.backend.product.domain;

import java.util.Locale;

/** 기획전 안 상품 정렬 기준. */
public enum ExhibitionProductSort {
    CURATED("기획 순서(기본). 기획자가 지정한 display_order 오름차순"),
    NEWEST("최신 등록순. 상품 created_at 내림차순"),
    PRICE_ASC("가격 낮은 순"),
    PRICE_DESC("가격 높은 순");

    private final String description;

    ExhibitionProductSort(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** 대소문자·'-' 표기를 무시하고 읽는다. 알 수 없는 값은 CURATED 로 본다. */
    public static ExhibitionProductSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return CURATED;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "NEWEST" -> NEWEST;
            case "PRICE_ASC" -> PRICE_ASC;
            case "PRICE_DESC" -> PRICE_DESC;
            default -> CURATED;
        };
    }
}
