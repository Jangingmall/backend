package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberSignupResult signUp(MemberSignupCommand command) {
        validate(command);

        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);

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
        return new MemberSignupResult(savedMember.getId(), savedMember.getEmail(), savedMember.getStatus());
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
