package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberStatus;
import java.time.Duration;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final EmailVerificationStore verificationStore;
    private final EmailSender emailSender;
    private final MemberRepository memberRepository;
    private final OneTimeTokenStore tokenStore;
    private final EmailVerificationProperties properties;

    public EmailVerificationService(EmailVerificationStore verificationStore, EmailSender emailSender) {
        this(verificationStore, emailSender, null, null, null);
    }

    @Autowired
    public EmailVerificationService(MemberRepository memberRepository, OneTimeTokenStore tokenStore,
        EmailSender emailSender, EmailVerificationProperties properties) {
        this(null, emailSender, memberRepository, tokenStore, properties);
    }

    private EmailVerificationService(EmailVerificationStore verificationStore, EmailSender emailSender,
        MemberRepository memberRepository, OneTimeTokenStore tokenStore, EmailVerificationProperties properties) {
        this.verificationStore = verificationStore;
        this.emailSender = emailSender;
        this.memberRepository = memberRepository;
        this.tokenStore = tokenStore;
        this.properties = properties;
    }

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

    @Transactional(readOnly = true)
    public long sendVerification(String email) {
        Member member = memberRepository.findByEmail(normalize(email))
            .filter(found -> found.getStatus() == MemberStatus.PENDING_VERIFICATION)
            .orElse(null);
        if (member != null) {
            issueAndSend(member.getId(), member.getEmail());
        }
        return properties.tokenExpirySeconds();
    }

    public void sendVerification(Long memberId, String email) {
        issueAndSend(memberId, normalize(email));
    }

    @Transactional
    public void verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        Long memberId = tokenStore.consume("signup-email", token)
            .map(this::parseMemberId)
            .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_EXPIRED));
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        member.activate();
    }

    private void issueAndSend(Long memberId, String email) {
        String token = tokenStore.issue("signup-email", memberId.toString(),
            Duration.ofSeconds(properties.tokenExpirySeconds()));
        URI link = UriComponentsBuilder.fromUri(properties.verificationUrl())
            .queryParam("token", token).build().encode().toUri();
        emailSender.sendVerificationLink(email, link.toString());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Long parseMemberId(String payload) {
        try {
            return Long.valueOf(payload);
        } catch (NumberFormatException exception) {
            throw new DomainException(ErrorCode.TOKEN_MISMATCH);
        }
    }
}
