package com.jangingmall.backend.product.domain;

import java.util.Locale;

/** 기획전 목록 정렬 기준. */
public enum ExhibitionSort {
    DISPLAY_ORDER("노출 순서(기본). 운영이 정한 display_order 오름차순"),
    NEWEST("최신 등록순. created_at 내림차순");

    private final String description;

    ExhibitionSort(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** 대소문자·'-' 표기를 무시하고 읽는다. 알 수 없는 값은 DISPLAY_ORDER 로 본다. */
    public static ExhibitionSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DISPLAY_ORDER;
        }
        return "NEWEST".equals(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')) ? NEWEST : DISPLAY_ORDER;
    }
}
