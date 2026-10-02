package com.jangingmall.backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 시연 목록 맨 앞 8개 순서 고정(V25)을 SQL 문자열로 검증한다. */
class DemoFixedListingOrderMigrationTest {

    @Test
    @DisplayName("합죽선부터 나전 명함집까지 8개가 지정한 순서대로 position 1..8 로 고정된다")
    void fixesTopEightInOrder() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V25__demo_fixed_listing_order.sql")
            .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\\((\\d), '([^']+)'\\)").matcher(sql);
        String[] expected = {"전주 합죽선 · 매화선", "청자 운학문 찻잔", "분청 귀얄 찻잔", "홍매 삼작 노리개",
            "수자수 모란도 액자", "한지 무드 조명", "하늘빛 한산모시 스카프", "나전 명함집"};
        int count = 0;
        while (matcher.find()) {
            assertThat(Integer.parseInt(matcher.group(1))).isEqualTo(count + 1);
            assertThat(matcher.group(2)).isEqualTo(expected[count]);
            count++;
        }
        assertThat(count).isEqualTo(8);
    }
}
