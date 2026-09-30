package com.jangingmall.backend.content.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiCatalogCodesTest {

    @ParameterizedTest(name = "재질 [{0}] → {1}")
    @CsvSource({
        "도자기, POTTERY",
        "백자, POTTERY",
        "청자, POTTERY",
        "옹기, ONGGI",
        "원목 옻칠, NACRE",
        "나전 원목, NACRE",
        "자개 금속, NACRE",
        "방짜유기, METAL",
        "방짜유기 은, METAL",
        "황동, METAL",
        "놋쇠, METAL",
        "한산모시, DYEING",
        "명주실 비단, DYEING",
        "한지 원목, DYEING",
        "원목, WOOD",
        "느티나무, WOOD",
        "대나무, WOOD",
        "대나무 유기, METAL"
    })
    @DisplayName("재질에서 챗봇 종목 코드를 추정한다")
    void craftCategoryFromMaterial(String material, String expected) {
        assertThat(AiCatalogCodes.craftCategory(material, null, null)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "재질 [{0}]은 종목을 추정하지 않는다")
    @ValueSource(strings = {"찹쌀", "녹차잎 100%", "대두 천일염", "유기농 쌀", "조청", "면", "아크릴"})
    @DisplayName("재질이 식품 등 챗봇 종목 밖이면 null이다(유기농의 '유기'는 놋그릇으로 오인하지 않는다)")
    void craftCategoryUnknown(String material) {
        assertThat(AiCatalogCodes.craftCategory(material, null, null)).isNull();
    }

    @Test
    @DisplayName("재질로 못 정하면 상품명, 그다음 소분류 이름으로 종목을 추정한다")
    void craftCategoryFallsBackToTitleThenSubcategory() {
        assertThat(AiCatalogCodes.craftCategory("아크릴", "청자 찻잔", "다기·찻잔")).isEqualTo("POTTERY");
        assertThat(AiCatalogCodes.craftCategory(null, "소반", "항아리·옹기")).isEqualTo("ONGGI");
        assertThat(AiCatalogCodes.craftCategory(null, null, null)).isNull();
    }

    @Test
    @DisplayName("재질이 있으면 재질이 상품명보다 우선한다")
    void craftCategoryMaterialWins() {
        assertThat(AiCatalogCodes.craftCategory("옹기", "청자 무늬 항아리", null)).isEqualTo("ONGGI");
    }

    @ParameterizedTest(name = "인증등급 [{0}] → {1}")
    @CsvSource({
        "국가무형유산, NATIONAL_INTANGIBLE_HERITAGE",
        "국가무형유산 보유자, NATIONAL_INTANGIBLE_HERITAGE",
        "명장, MASTER_CRAFTSMAN",
        "숙련장인, SENIOR_CRAFTSMAN",
        "청년장인, YOUNG_CRAFTSMAN",
        "MASTER_CRAFTSMAN, MASTER_CRAFTSMAN",
        "master_craftsman, MASTER_CRAFTSMAN"
    })
    @DisplayName("한글·영문 인증등급을 챗봇 코드로 바꾼다")
    void certificationLevel(String level, String expected) {
        assertThat(AiCatalogCodes.certificationLevel(level)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"일반", "  ", "알 수 없음"})
    @DisplayName("'일반'이나 모르는 인증등급은 null이다")
    void certificationLevelUnknown(String level) {
        assertThat(AiCatalogCodes.certificationLevel(level)).isNull();
    }

    @ParameterizedTest(name = "색상 [{0}] → {1}")
    @CsvSource({
        "흰색, WHITE",
        "하얀색, WHITE",
        "검정색, BLACK",
        "검은색, BLACK",
        "회색, GRAY",
        "빨간색, RED",
        "파란색, BLUE",
        "초록색, GREEN",
        "갈색, BROWN",
        "WHITE, WHITE",
        "brown, BROWN"
    })
    @DisplayName("한글 색상명을 챗봇 색상 코드로 바꾼다")
    void color(String color, String expected) {
        assertThat(AiCatalogCodes.color(color)).isEqualTo(expected);
    }

    @Test
    @DisplayName("챗봇 색상 목록에 없는 색은 원문을 그대로 보내고, 비어 있으면 null이다")
    void colorUnknownKeepsRaw() {
        assertThat(AiCatalogCodes.color("자연색")).isEqualTo("자연색");
        assertThat(AiCatalogCodes.color(" 베이지 ")).isEqualTo("베이지");
        assertThat(AiCatalogCodes.color(null)).isNull();
        assertThat(AiCatalogCodes.color(" ")).isNull();
    }

    @Test
    @DisplayName("한글 선물 테마를 챗봇 코드로 바꾸고 중복은 제거하며 순서를 지킨다")
    void giftThemes() {
        assertThat(AiCatalogCodes.giftThemes(List.of("생일", "결혼식", "부모님", "생일 선물")))
            .containsExactly("BIRTHDAY", "WEDDING", "PARENTS");
        assertThat(AiCatalogCodes.giftThemes(List.of("환갑 생일", "집들이", "승진 축하", "친구", "연인")))
            .containsExactly("BIRTHDAY_60TH", "HOUSEWARMING", "PROMOTION", "FRIEND", "COUPLE");
    }

    @Test
    @DisplayName("이미 챗봇 코드인 선물 테마는 그대로 두고, 모르는 테마는 원문을 유지한다")
    void giftThemesPassThrough() {
        assertThat(AiCatalogCodes.giftThemes(List.of("PARENTS", "parents", "전통공예"))).containsExactly("PARENTS", "전통공예");
    }

    @Test
    @DisplayName("선물 테마가 null이거나 빈 값뿐이면 빈 목록이다")
    void giftThemesEmpty() {
        assertThat(AiCatalogCodes.giftThemes(null)).isEmpty();
        assertThat(AiCatalogCodes.giftThemes(Arrays.asList(null, " "))).isEmpty();
    }
}
