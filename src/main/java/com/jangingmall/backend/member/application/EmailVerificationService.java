package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final EmailVerificationStore verificationStore;
    private final EmailSender emailSender;

    public void sendCode(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        verificationStore.save(normalized, code, CODE_TTL);
        emailSender.sendVerificationCode(normalized, code);
    }

    public void verify(String email, String code) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String stored = verificationStore.consumeCode(normalized)
            .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_EXPIRED));
        if (!stored.equals(code.trim())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }
}
