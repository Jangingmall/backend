package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberAuthenticationService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional(readOnly = true)
    public MemberSession login(String email, String password) {
        Member member = memberRepository.findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED));

        if (!member.canLogIn()) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (member.getPasswordHash() == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }

        return issueSession(member);
    }

    @Transactional(readOnly = true)
    public MemberSession refresh(String refreshToken) {
        JwtTokenProvider.JwtMemberClaims claims;
        try {
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (RuntimeException exception) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }

        if (!refreshTokenStore.matches(claims.memberId(), refreshToken)) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.findById(claims.memberId())
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED));
        if (!member.canLogIn()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }

        return issueSession(member);
    }

    public void logout(Long memberId) {
        refreshTokenStore.delete(memberId);
    }

    @Transactional(readOnly = true)
    public MemberProfile getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new MemberProfile(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }

    private MemberSession issueSession(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), member.getRole());
        refreshTokenStore.save(
            member.getId(),
            refreshToken,
            Duration.ofMillis(jwtProperties.refreshTokenExpiry())
        );
        return new MemberSession(
            accessToken,
            refreshToken,
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getRole()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
