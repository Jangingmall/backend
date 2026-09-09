package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberStatus;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationTokenStore tokenStore;
    private final VerificationEmailSender emailSender;
    private final EmailVerificationProperties properties;

    @Transactional(readOnly = true)
    public long sendVerification(String email) {
        memberRepository.findByEmail(normalizeEmail(email))
            .filter(member -> member.getStatus() == MemberStatus.PENDING_VERIFICATION)
            .ifPresent(member -> issueAndSend(member.getId(), member.getEmail()));
        return properties.tokenExpirySeconds();
    }

    public void sendVerification(Long memberId, String email) {
        issueAndSend(memberId, email);
    }

    @Transactional
    public void verify(String token) {
        if (token == null || token.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }

        Long memberId = tokenStore.consume(token)
            .orElseThrow(() -> new DomainException(ErrorCode.TOKEN_EXPIRED));
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        member.activate();
    }

    private void issueAndSend(Long memberId, String email) {
        String token = tokenStore.issue(
            memberId,
            Duration.ofSeconds(properties.tokenExpirySeconds())
        );
        emailSender.send(email, token);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
