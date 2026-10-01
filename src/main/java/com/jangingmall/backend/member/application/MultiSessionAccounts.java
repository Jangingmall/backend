package com.jangingmall.backend.member.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 같은 계정으로 여러 기기·브라우저에서 동시에 로그인해도 서로의 리프레시 토큰을 무효화하지 않는 테스트용 계정 목록.
 * 일반 회원은 기존대로 재로그인하면 이전 리프레시 토큰이 무효가 된다.
 */
@Component
public class MultiSessionAccounts {

    private final Set<String> emails;

    public MultiSessionAccounts(@Value("${member.multi-session-emails:}") String emails) {
        this.emails = Arrays.stream(emails.split(","))
            .map(email -> email.trim().toLowerCase(Locale.ROOT))
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
