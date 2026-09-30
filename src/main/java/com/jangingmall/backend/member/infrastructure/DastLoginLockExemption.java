package com.jangingmall.backend.member.infrastructure;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DAST 계정(yml 주입 고정 이메일)의 로그인 잠금을 기한 한정으로 면제한다.
 * 만료일(dast.lock-exempt-until, 포함) 이후이거나 미설정·파싱 불가면 면제하지 않는다.
 */
@Component
public class DastLoginLockExemption {

    private final Set<String> emails;
    private final LocalDate exemptUntil;
    private final Clock clock;

    @Autowired
    public DastLoginLockExemption(
        @Value("${dast.user.email:}") String userEmail,
        @Value("${dast.artisan.email:}") String artisanEmail,
        @Value("${dast.admin.email:}") String adminEmail,
        @Value("${dast.lock-exempt-until:}") String exemptUntil
    ) {
        this(userEmail, artisanEmail, adminEmail, exemptUntil, Clock.systemDefaultZone());
    }

    DastLoginLockExemption(String userEmail, String artisanEmail, String adminEmail, String exemptUntil, Clock clock) {
        this.emails = Stream.of(userEmail, artisanEmail, adminEmail)
            .map(email -> email.trim().toLowerCase(Locale.ROOT))
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        this.exemptUntil = parse(exemptUntil);
        this.clock = clock;
    }

    public boolean isExempt(String normalizedEmail) {
        return exemptUntil != null
            && emails.contains(normalizedEmail)
            && !LocalDate.now(clock).isAfter(exemptUntil);
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
