package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberSignupResult signUp(MemberSignupCommand command) {
        validate(command);

        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
        log.info("[회원가입] email={} password={}",
            maskEmail(normalizedEmail),
            maskPassword(command.password()));

        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new DomainException(ErrorCode.CONFLICT);
        }

        Member member = Member.register(
            normalizedEmail,
            passwordEncoder.encode(command.password()),
            command.name(),
            command.phone(),
            command.role(),
            command.age14OrOlder(),
            command.termsOfService(),
            command.privacyCollection(),
            command.marketing()
        );

        Member savedMember = memberRepository.save(member);
        return MemberSignupResult.from(savedMember);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskPassword(String password) {
        if (password.length() <= 1) return "***";
        return password.charAt(0) + "***";
    }

    private void validate(MemberSignupCommand command) {
        if (command.role() != MemberRole.USER) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (!command.password().equals(command.passwordConfirm())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (!command.age14OrOlder() || !command.termsOfService() || !command.privacyCollection()) {
            throw new DomainException(ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }
}
