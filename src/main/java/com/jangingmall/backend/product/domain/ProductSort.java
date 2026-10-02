package com.jangingmall.backend.product.domain;

import java.util.Locale;

/** 상품 목록 정렬 기준. 같은 값이면 항상 id 내림차순으로 순서를 고정한다. */
public enum ProductSort {
    NEWEST("최신 등록순(기본). created_at 내림차순"),
    POPULAR("인기순. 점수 = 운영 노출 가중치 + 판매수×3 + 찜수×2 + 리뷰수 (판매수는 결제 완료 이후 주문의 수량 합), 점수 내림차순"),
    SALES_COUNT("판매순. 결제 완료(PAID·IN_DELIVERY·DELIVERED·PURCHASE_CONFIRMED) 주문의 수량 합 내림차순"),
    WISHLIST_COUNT("찜 많은 순. 찜한 회원 수 내림차순"),
    PRICE_ASC("가격 낮은 순"),
    PRICE_DESC("가격 높은 순");

    private final String description;

    ProductSort(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** 대소문자와 '-'·공백 표기를 무시하고 읽는다. 알 수 없는 값은 NEWEST 로 본다. */
    public static ProductSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "POPULAR", "BEST" -> POPULAR;
            case "SALES_COUNT", "SALES" -> SALES_COUNT;
            case "WISHLIST_COUNT", "WISHLIST" -> WISHLIST_COUNT;
            case "PRICE_ASC" -> PRICE_ASC;
            case "PRICE_DESC" -> PRICE_DESC;
            default -> NEWEST;
        };
    }
}
