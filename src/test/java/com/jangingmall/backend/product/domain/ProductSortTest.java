package com.jangingmall.backend.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductSortTest {

    @Test
    @DisplayName("프론트 URL 표기와 API enum 을 대소문자·하이픈 무관하게 읽는다")
    void parsesFrontendAndApiNames() {
        assertThat(ProductSort.from("popular")).isEqualTo(ProductSort.POPULAR);
        assertThat(ProductSort.from("SALES_COUNT")).isEqualTo(ProductSort.SALES_COUNT);
        assertThat(ProductSort.from("sales")).isEqualTo(ProductSort.SALES_COUNT);
        assertThat(ProductSort.from("wishlist")).isEqualTo(ProductSort.WISHLIST_COUNT);
        assertThat(ProductSort.from("price-asc")).isEqualTo(ProductSort.PRICE_ASC);
        assertThat(ProductSort.from("PRICE_DESC")).isEqualTo(ProductSort.PRICE_DESC);
    }

    @Test
    @DisplayName("비어 있거나 모르는 값은 NEWEST 이고 모든 enum 에 설명이 있다")
    void fallsBackToNewestAndEveryValueIsDescribed() {
        assertThat(ProductSort.from(null)).isEqualTo(ProductSort.NEWEST);
        assertThat(ProductSort.from(" ")).isEqualTo(ProductSort.NEWEST);
        assertThat(ProductSort.from("toString")).isEqualTo(ProductSort.NEWEST);
        for (ProductSort sort : ProductSort.values()) {
            assertThat(sort.description()).isNotBlank();
        }
    }
}
