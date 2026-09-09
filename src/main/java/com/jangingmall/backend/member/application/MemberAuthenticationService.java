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

        Member member = memberRepository.findById(claims.memberId())
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED));
        if (!member.canLogIn()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }

        MemberSession session = buildSession(member);
        if (!refreshTokenStore.rotate(member.getId(), refreshToken, session.refreshToken(),
            Duration.ofMillis(jwtProperties.refreshTokenExpiry()))) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        return session;
    }

    public void logout(Long memberId) {
        refreshTokenStore.delete(memberId);
    }

    @Transactional(readOnly = true)
    public MemberProfile getProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!member.canLogIn()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        return MemberProfile.from(member);
    }

    private MemberSession issueSession(Member member) {
        MemberSession session = buildSession(member);
        refreshTokenStore.save(member.getId(), session.refreshToken(), Duration.ofMillis(jwtProperties.refreshTokenExpiry()));
        return session;
    }

    @Transactional(readOnly = true)
    public MemberSession socialSession(Long memberId) {
        Member member = memberRepository.findById(memberId).filter(Member::canLogIn)
            .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED));
        return issueSession(member);
    }

    private MemberSession buildSession(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), member.getRole());
        return new MemberSession(
            accessToken,
            refreshToken,
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getRole(), member.getNickname(), member.getProfileImageUrl()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
