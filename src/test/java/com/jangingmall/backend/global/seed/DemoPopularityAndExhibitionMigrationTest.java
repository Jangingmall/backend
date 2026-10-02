package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 인기순 가중치·시연 판매/찜 시드(V28)와 기획전 테이블·시드(V29)를 SQL 문자열로 검증한다. */
class DemoPopularityAndExhibitionMigrationTest {

    private static String read(String name) throws Exception {
        return new String(new ClassPathResource("db/migration/" + name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("V28 은 가중치 컬럼을 추가하고 기본 목록 맨 앞 8개를 1000 부터 10씩 줄여 고정한다")
    void popularityBoostFixesTopEight() throws Exception {
        String sql = read("V28__product_popularity.sql");
        assertThat(sql).contains("ADD COLUMN popularity_boost INT NOT NULL DEFAULT 0");
        String[] titles = {"전주 합죽선 · 매화선", "청자 운학문 찻잔", "분청 귀얄 찻잔", "홍매 삼작 노리개",
            "수자수 모란도 액자", "한지 무드 조명", "하늘빛 한산모시 스카프", "나전 명함집"};
        for (int i = 0; i < titles.length; i++) {
            assertThat(sql).contains("('" + titles[i] + "', " + (1000 - 10 * i) + ")");
        }
    }

    @Test
    @DisplayName("V28 은 베스트 5개에 판매 주문을, 기획전 4개에 찜을 중복 없이 심는다")
    void seedsBestSalesAndPlanWishlist() throws Exception {
        String sql = read("V28__product_popularity.sql");
        assertThat(sql).contains("(1, '청자 분청 찻잔', 10)").contains("(5, '왕골 원형 부채', 6)").contains("PURCHASE_CONFIRMED")
            .contains("('대나무 조명', 9)").contains("('산수화 대형 부채', 6)").contains("ON CONFLICT (member_id, product_id) DO NOTHING");
    }

    @Test
    @DisplayName("V29 는 기획전·기획전 상품 테이블을 만들고 시연 기획전 2개를 심는다")
    void createsExhibitions() throws Exception {
        String sql = read("V29__exhibition.sql");
        assertThat(sql).contains("CREATE TABLE exhibition (").contains("CREATE TABLE exhibition_product (")
            .contains("장인이 빚은 공간의 온기").contains("바람을 부르는 부채 모음");
    }
}
