package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class DastLoginLockExemptionTest {

    private static Clock at(String date) {
        return Clock.fixed(Instant.parse(date + "T03:00:00Z"), ZoneId.of("UTC"));
    }

    private static DastLoginLockExemption exemption(String until, Clock clock) {
        return new DastLoginLockExemption("User@Dast.com", "artisan@dast.com", "admin@dast.com", until, "", clock);
    }

    private static DastLoginLockExemption ipExemption(String until, String ipList, Clock clock) {
        return new DastLoginLockExemption("", "", "", until, ipList, clock);
    }

    private static void requestFrom(String remoteAddr) {
        var request = new MockHttpServletRequest("POST", "/api/member/login");
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
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
        var blank = new DastLoginLockExemption("", "", "", "2026-10-05", "", at("2026-10-01"));
        assertThat(blank.isExempt("")).isFalse();
    }

    @Test
    @DisplayName("공백으로 구분한 IP 목록의 요청은 어떤 이메일이든 면제된다")
    void exemptByIp() {
        var exemption = ipExemption("2026-10-05", "127.0.0.1  127.0.0.2\t127.0.0.3", at("2026-10-01"));

        requestFrom("127.0.0.2");
        assertThat(exemption.isExempt("anyone@example.com")).isTrue();
        requestFrom("127.0.0.3");
        assertThat(exemption.isExempt("anyone@example.com")).isTrue();
    }

    @Test
    @DisplayName("목록에 없는 IP이거나 요청 컨텍스트가 없으면 면제되지 않는다")
    void notExemptForOtherIp() {
        var exemption = ipExemption("2026-10-05", "127.0.0.1 127.0.0.2", at("2026-10-01"));

        requestFrom("10.0.0.1");
        assertThat(exemption.isExempt("anyone@example.com")).isFalse();
        RequestContextHolder.resetRequestAttributes();
        assertThat(exemption.isExempt("anyone@example.com")).isFalse();
    }

    @Test
    @DisplayName("IP 면제도 만료일이 지나면 해제된다")
    void ipExemptionExpires() {
        requestFrom("127.0.0.1");

        assertThat(ipExemption("2026-10-05", "127.0.0.1", at("2026-10-06")).isExempt("a@example.com")).isFalse();
        assertThat(ipExemption("", "127.0.0.1", at("2026-10-01")).isExempt("a@example.com")).isFalse();
    }

    @Test
    @DisplayName("IP 목록이 비어 있으면 아무도 면제되지 않는다")
    void blankIpList() {
        requestFrom("127.0.0.1");

        assertThat(ipExemption("2026-10-05", "  ", at("2026-10-01")).isExempt("a@example.com")).isFalse();
    }
}
