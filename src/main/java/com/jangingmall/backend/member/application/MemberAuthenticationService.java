package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberSocialAccountRepository;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberAuthenticationService {

    static final String LOGIN_LOCKED_MESSAGE = "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;
    private final MemberSocialAccountRepository socialAccounts;
    private final LoginAttemptService loginAttempts;

    @Transactional(readOnly = true)
    public MemberSession login(String email, String password) {
        String normalized = normalizeEmail(email);
        if (loginAttempts.isLocked(normalized)) {
            throw new DomainException(ErrorCode.TOO_MANY_REQUESTS, LOGIN_LOCKED_MESSAGE);
        }
        Member member = memberRepository.findByEmail(normalized)
            .orElseThrow(() -> {
                loginAttempts.recordFailure(normalized);
                return new DomainException(ErrorCode.UNAUTHORIZED);
            });

        if (!member.canLogIn()) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (member.getPasswordHash() == null || !passwordEncoder.matches(password, member.getPasswordHash())) {
            loginAttempts.recordFailure(normalized);
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }

        loginAttempts.clearFailures(normalized);
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

        // 리프레시 토큰은 로그인 때 발급된 값을 만료(7일)까지 그대로 쓴다. 갱신할 때마다 교체하지 않으므로 같은 토큰으로
        // 동시에 여러 번 갱신해도 모두 성공한다. 로그아웃·비밀번호 변경·재로그인으로 서버 저장값이 바뀌면 이 토큰은 거절된다.
        if (!refreshTokenStore.matches(member.getId(), refreshToken)) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        return sessionOf(member, jwtTokenProvider.createAccessToken(member.getId(), member.getRole()), refreshToken);
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
        return MemberProfile.from(member, providerOf(member.getId()));
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
        return sessionOf(member, accessToken, refreshToken);
    }

    private MemberSession sessionOf(Member member, String accessToken, String refreshToken) {
        return new MemberSession(
            accessToken,
            refreshToken,
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getRole(), member.getNickname(), member.getProfileImageUrl(), providerOf(member.getId()), member.getPhone()
        );
    }

    private String providerOf(Long memberId) {
        return socialAccounts.findFirstByMemberIdOrderByIdAsc(memberId)
            .map(account -> account.getRegistrationId())
            .orElse(null);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
