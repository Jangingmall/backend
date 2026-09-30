package com.jangingmall.backend.content.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 챗봇 서버(GenAI chat_bot)가 기대하는 영문 코드로 백엔드 카탈로그 값을 바꾼다.
 *
 * <p>챗봇은 종목(POTTERY 등)·색상(WHITE 등)·선물테마(PARENTS 등)·인증등급(MASTER_CRAFTSMAN 등)을 영문 코드로 저장하고
 * 그 코드로 임베딩 문구·라벨·필터를 만든다. 백엔드는 한글 자유 문자열(소분류 이름, "흰색", "일반" 등)을 갖고 있어서 그대로 보내면
 * 챗봇이 색상·등급을 "정보 없음"으로 답하고 종목이 검색에 반영되지 않는다.
 *
 * <p>규칙은 모두 결정적이고 보수적이다. 확실히 매핑되지 않는 값은 잘못 추정하지 않고 종목·인증등급은 null, 색상·선물테마는
 * 원문 그대로 보낸다(챗봇은 모르는 값을 "정보 없음"으로 다루거나 원문 문구로 임베딩한다).
 */
public final class AiCatalogCodes {

    /** 종목 규칙. 앞선 규칙이 우선한다(예: "자개 금속"은 NACRE). 재질 → 상품명 → 소분류 순으로 같은 규칙을 적용한다. */
    private static final Map<String, List<String>> CRAFT_RULES = new LinkedHashMap<>();
    /** 챗봇 taxonomy의 종목 코드. */
    private static final Set<String> CRAFT_CODES =
        Set.of("POTTERY", "ONGGI", "NACRE", "DYEING", "WOOD", "METAL");

    static {
        CRAFT_RULES.put("ONGGI", List.of("옹기", "장독", "김치독"));
        CRAFT_RULES.put("POTTERY", List.of("도자기", "백자", "청자", "분청", "세라믹", "도예", "다완"));
        CRAFT_RULES.put("NACRE", List.of("나전", "자개", "옻칠", "칠기"));
        CRAFT_RULES.put("METAL", List.of("방짜유기", "유기", "황동", "놋쇠", "백동", "무쇠", "두석", "신주", "청동", "금속",
            "순은", "칠보"));
        CRAFT_RULES.put("DYEING", List.of("염색", "한산모시", "모시", "삼베", "명주", "실크", "비단", "양단", "리넨", "한지"));
        CRAFT_RULES.put("WOOD", List.of("원목", "나무", "목재", "목공", "대나무", "오죽"));
    }

    private static final Map<String, List<String>> COLOR_RULES = new LinkedHashMap<>();

    static {
        COLOR_RULES.put("WHITE", List.of("흰", "하양", "하얀", "화이트", "백색"));
        COLOR_RULES.put("BLACK", List.of("검", "블랙", "흑색"));
        COLOR_RULES.put("GRAY", List.of("회색", "그레이"));
        COLOR_RULES.put("RED", List.of("빨", "적색", "레드"));
        COLOR_RULES.put("BLUE", List.of("파랑", "파란", "청색", "블루", "푸른"));
        COLOR_RULES.put("GREEN", List.of("초록", "녹색", "그린"));
        COLOR_RULES.put("BROWN", List.of("갈색", "브라운"));
    }

    /** 앞선 규칙이 우선한다("환갑 생일"은 BIRTHDAY_60TH). */
    private static final Map<String, List<String>> GIFT_THEME_RULES = new LinkedHashMap<>();

    static {
        GIFT_THEME_RULES.put("BIRTHDAY_60TH", List.of("환갑", "회갑", "60세", "육순"));
        GIFT_THEME_RULES.put("BIRTHDAY", List.of("생일", "돌잔치", "돌선물"));
        GIFT_THEME_RULES.put("PARENTS", List.of("부모", "어버이", "엄마", "아빠", "어머니", "아버지"));
        GIFT_THEME_RULES.put("BOSS", List.of("상사", "윗사람", "스승"));
        GIFT_THEME_RULES.put("CORPORATE", List.of("거래처", "비즈니스", "기업", "회사", "답례품"));
        GIFT_THEME_RULES.put("COUPLE", List.of("연인", "커플", "기념일"));
        GIFT_THEME_RULES.put("FRIEND", List.of("친구", "동료"));
        GIFT_THEME_RULES.put("HOUSEWARMING", List.of("집들이", "입주", "새집"));
        GIFT_THEME_RULES.put("PROMOTION", List.of("승진", "취임", "영전"));
        GIFT_THEME_RULES.put("WEDDING", List.of("결혼", "혼수", "웨딩", "신혼", "예단"));
    }

    private static final Set<String> GIFT_THEME_CODES = GIFT_THEME_RULES.keySet();
    private static final Set<String> COLOR_CODES = COLOR_RULES.keySet();
    private static final Set<String> CERTIFICATION_CODES = Set.of(
        "YOUNG_CRAFTSMAN", "SENIOR_CRAFTSMAN", "MASTER_CRAFTSMAN", "NATIONAL_INTANGIBLE_HERITAGE");

    private AiCatalogCodes() {}

    /** 재질·상품명·소분류 이름에서 챗봇 종목 코드를 추정한다. 근거가 없으면 null. */
    public static String craftCategory(String material, String title, String subcategoryName) {
        for (String text : new String[] {material, title, subcategoryName}) {
            String matched = matchFirst(CRAFT_RULES, text);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    /** "명장"·"국가무형유산" 등을 챗봇 인증등급 코드로 바꾼다. "일반"이나 모르는 값은 null. */
    public static String certificationLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        String trimmed = level.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (CERTIFICATION_CODES.contains(upper)) {
            return upper;
        }
        if (containsAny(trimmed, "국가무형유산", "무형문화재", "인간문화재")) {
            return "NATIONAL_INTANGIBLE_HERITAGE";
        }
        if (containsAny(trimmed, "명장", "명인")) {
            return "MASTER_CRAFTSMAN";
        }
        if (containsAny(trimmed, "숙련", "중견")) {
            return "SENIOR_CRAFTSMAN";
        }
        if (containsAny(trimmed, "청년", "신진")) {
            return "YOUNG_CRAFTSMAN";
        }
        return null;
    }

    /** 한글 색상명을 챗봇 색상 코드로 바꾼다. 모르는 색(자연색, 베이지 등)은 원문을 그대로 돌려준다. */
    public static String color(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String trimmed = color.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (COLOR_CODES.contains(upper)) {
            return upper;
        }
        String matched = matchFirst(COLOR_RULES, trimmed);
        return matched != null ? matched : trimmed;
    }

    /** 한글 선물 테마를 챗봇 코드로 바꾼다. 모르는 값은 원문 유지, 중복은 제거하고 순서는 보존한다. */
    public static List<String> giftThemes(List<String> themes) {
        if (themes == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String theme : themes) {
            if (theme == null || theme.isBlank()) {
                continue;
            }
            String trimmed = theme.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (GIFT_THEME_CODES.contains(upper)) {
                result.add(upper);
                continue;
            }
            String matched = matchFirst(GIFT_THEME_RULES, trimmed);
            result.add(matched != null ? matched : trimmed);
        }
        return new ArrayList<>(result);
    }

    /** 이미 챗봇 종목 코드인지. */
    public static boolean isCraftCode(String value) {
        return value != null && CRAFT_CODES.contains(value);
    }

    private static String matchFirst(Map<String, List<String>> rules, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        // "유기농"의 "유기"를 놋그릇(유기)으로 오인하지 않도록 먼저 지운다.
        String text = rawText.replace("유기농", "");
        for (Map.Entry<String, List<String>> rule : rules.entrySet()) {
            if (containsAny(text, rule.getValue().toArray(String[]::new))) {
                return rule.getKey();
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
