package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberSocialAccount;
import com.jangingmall.backend.member.domain.MemberSocialAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberAuthenticationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private MemberSocialAccountRepository socialAccounts;

    @Mock
    private LoginAttemptService loginAttempts;

    private MemberAuthenticationService memberAuthenticationService;

    @BeforeEach
    void setUp() {
        memberAuthenticationService = new MemberAuthenticationService(
            memberRepository,
            passwordEncoder,
            jwtTokenProvider,
            new JwtProperties(
                "local-dev-secret-key-minimum-256-bits-for-hs256-algorithm",
                1_800_000,
                604_800_000,
                false
            ),
            refreshTokenStore,
            socialAccounts,
            loginAttempts
        );
    }

    @Test
    @DisplayName("활성 회원은 로그인 시 Access·Refresh Token을 발급한다")
    void login() {
        Member member = activeMember();
        when(memberRepository.findByEmail("artisan@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password", member.getPasswordHash())).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(1L, MemberRole.USER)).thenReturn("refresh-token");

        MemberSession session = memberAuthenticationService.login("ARTISAN@EXAMPLE.COM", "password");

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.refreshToken()).isEqualTo("refresh-token");
        assertThat(session.provider()).isNull();
        verify(refreshTokenStore).save(1L, "refresh-token", java.time.Duration.ofDays(7));
    }

    @Test
    @DisplayName("소셜 회원 세션에는 가입한 provider를 포함한다")
    void socialSessionIncludesProvider() {
        Member member = activeMember();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(socialAccounts.findFirstByMemberIdOrderByIdAsc(1L))
            .thenReturn(Optional.of(new MemberSocialAccount(1L, "kakao", "provider-id", "artisan@example.com")));
        when(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(1L, MemberRole.USER)).thenReturn("refresh-token");

        MemberSession session = memberAuthenticationService.socialSession(1L);

        assertThat(session.provider()).isEqualTo("kakao");
    }

    @Test
    @DisplayName("잘못된 비밀번호는 UNAUTHORIZED를 반환한다")
    void loginWithInvalidPassword() {
        Member member = activeMember();
        when(memberRepository.findByEmail("artisan@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", member.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> memberAuthenticationService.login("artisan@example.com", "wrong-password"))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("로그인 시도가 잠긴 이메일은 조회 없이 TOO_MANY_REQUESTS를 반환한다")
    void loginWhenLocked() {
        when(loginAttempts.isLocked("artisan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberAuthenticationService.login("ARTISAN@example.com", "password"))
            .isInstanceOfSatisfying(DomainException.class, exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                assertThat(exception.getMessage()).isEqualTo(MemberAuthenticationService.LOGIN_LOCKED_MESSAGE);
            });
        verifyNoInteractions(memberRepository);
    }

    @Test
    @DisplayName("유효한 Refresh Token은 교체 없이 새 Access Token만 발급하고, 같은 토큰으로 여러 번 갱신할 수 있다")
    void refreshIssuesAccessTokenWithoutRotation() {
        Member member = activeMember();
        when(jwtTokenProvider.parseRefreshToken("refresh-token"))
            .thenReturn(new JwtTokenProvider.JwtMemberClaims(1L, MemberRole.USER));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).thenReturn("new-access-token");
        when(refreshTokenStore.matches(1L, "refresh-token")).thenReturn(true);

        MemberSession first = memberAuthenticationService.refresh("refresh-token");
        MemberSession second = memberAuthenticationService.refresh("refresh-token");

        assertThat(first.accessToken()).isEqualTo("new-access-token");
        assertThat(first.refreshToken()).isEqualTo("refresh-token");
        assertThat(second.refreshToken()).isEqualTo("refresh-token");
        verify(jwtTokenProvider, never()).createRefreshToken(any(), any());
        verify(refreshTokenStore, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("서버에서 무효화된 Refresh Token은 재사용할 수 없다")
    void rejectsInvalidatedRefreshToken() {
        when(jwtTokenProvider.parseRefreshToken("logged-out-token"))
            .thenReturn(new JwtTokenProvider.JwtMemberClaims(1L, MemberRole.USER));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember()));
        when(refreshTokenStore.matches(1L, "logged-out-token")).thenReturn(false);

        assertThatThrownBy(() -> memberAuthenticationService.refresh("logged-out-token"))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(jwtTokenProvider, never()).createAccessToken(any(), any());
    }

    @Test
    @DisplayName("로그아웃은 서버의 Refresh Token을 삭제한다")
    void logoutInvalidatesRefreshToken() {
        memberAuthenticationService.logout(1L);

        verify(refreshTokenStore).delete(1L);
    }

    private Member activeMember() {
        Member member = member();
        member.activate();
        return member;
    }

    private Member member() {
        Member member = Member.register(
            "artisan@example.com",
            "hashed-password",
            "김도공",
            "01012345678",
            MemberRole.USER,
            true,
            true,
            true,
            false
        );
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
