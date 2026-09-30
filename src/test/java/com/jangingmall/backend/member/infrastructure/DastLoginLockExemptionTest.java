package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DastLoginLockExemptionTest {

    private static Clock at(String date) {
        return Clock.fixed(Instant.parse(date + "T03:00:00Z"), ZoneId.of("UTC"));
    }

    private static DastLoginLockExemption exemption(String until, Clock clock) {
        return new DastLoginLockExemption("User@Dast.com", "artisan@dast.com", "admin@dast.com", until, clock);
    }

    @Test
    @DisplayName("만료일(포함)까지 DAST 계정은 잠금에서 면제된다")
    void exemptUntilInclusive() {
        assertThat(exemption("2026-10-05", at("2026-10-05")).isExempt("user@dast.com")).isTrue();
        assertThat(exemption("2026-10-05", at("2026-09-30")).isExempt("admin@dast.com")).isTrue();
    }

    @Test
    @DisplayName("만료일이 지나면 면제되지 않는다")
    void notExemptAfterExpiry() {
        assertThat(exemption("2026-10-05", at("2026-10-06")).isExempt("user@dast.com")).isFalse();
    }

    @Test
    @DisplayName("DAST 계정이 아니면 면제되지 않는다")
    void notExemptForOtherAccounts() {
        assertThat(exemption("2026-10-05", at("2026-10-01")).isExempt("someone@example.com")).isFalse();
    }

    @Test
    @DisplayName("만료일이 비었거나 형식이 잘못되면 면제되지 않는다")
    void notExemptWithoutValidDate() {
        assertThat(exemption("", at("2026-10-01")).isExempt("user@dast.com")).isFalse();
        assertThat(exemption("not-a-date", at("2026-10-01")).isExempt("user@dast.com")).isFalse();
    }

    @Test
    @DisplayName("이메일이 비어 있으면 빈 문자열도 면제하지 않는다")
    void blankEmailsNeverExempt() {
        var blank = new DastLoginLockExemption("", "", "", "2026-10-05", at("2026-10-01"));
        assertThat(blank.isExempt("")).isFalse();
    }
}
