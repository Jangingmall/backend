package com.jangingmall.backend.member.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * DAST 계정(yml 주입 고정 이메일) 또는 DAST IP(DART_IP_LIST, 공백 구분)의 로그인 잠금을 기한 한정으로 면제한다.
 * 만료일(dast.lock-exempt-until, 포함) 이후이거나 미설정·파싱 불가면 어느 쪽도 면제하지 않는다.
 * IP는 request.getRemoteAddr() 기준이며, X-Forwarded-For는 신뢰하지 않는다(스푸핑 방지).
 */
@Component
public class DastLoginLockExemption {

    private final Set<String> emails;
    private final Set<String> ips;
    private final LocalDate exemptUntil;
    private final Clock clock;

    @Autowired
    public DastLoginLockExemption(
        @Value("${dast.user.email:}") String userEmail,
        @Value("${dast.artisan.email:}") String artisanEmail,
        @Value("${dast.admin.email:}") String adminEmail,
        @Value("${dast.lock-exempt-until:}") String exemptUntil,
        @Value("${dast.ip-list:}") String ipList
    ) {
        this(userEmail, artisanEmail, adminEmail, exemptUntil, ipList, Clock.systemDefaultZone());
    }

    DastLoginLockExemption(String userEmail, String artisanEmail, String adminEmail, String exemptUntil,
                           String ipList, Clock clock) {
        this.emails = Stream.of(userEmail, artisanEmail, adminEmail)
            .map(email -> email.trim().toLowerCase(Locale.ROOT))
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        this.ips = ipList == null || ipList.isBlank()
            ? Set.of()
            : Set.copyOf(Arrays.asList(ipList.trim().split("\\s+")));
        this.exemptUntil = parse(exemptUntil);
        this.clock = clock;
    }

    public boolean isExempt(String normalizedEmail) {
        if (exemptUntil == null || LocalDate.now(clock).isAfter(exemptUntil)) {
            return false;
        }
        if (emails.contains(normalizedEmail)) {
            return true;
        }
        String clientIp = currentClientIp();
        return clientIp != null && ips.contains(clientIp);
    }

    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
