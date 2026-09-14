package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberStatus;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EmailVerificationTokenStore tokenStore;

    @Mock
    private VerificationEmailSender emailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
            memberRepository,
            tokenStore,
            emailSender,
            properties()
        );
    }

    @Test
    @DisplayName("인증 대기 회원에게 새 토큰을 발급해 메일을 보낸다")
    void sendVerification() {
        Member member = pendingMember();
        when(memberRepository.findByEmail("artisan@example.com")).thenReturn(Optional.of(member));
        when(tokenStore.issue(1L, Duration.ofSeconds(1800))).thenReturn("verification-token");

        long expiresInSeconds = service.sendVerification(" ARTISAN@example.com ");

        assertThat(expiresInSeconds).isEqualTo(1800);
        verify(emailSender).send("artisan@example.com", "verification-token");
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 계정 존재 여부를 숨기기 위해 동일한 응답을 반환한다")
    void hidesUnknownEmail() {
        when(memberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThat(service.sendVerification("unknown@example.com")).isEqualTo(1800);

        verifyNoInteractions(tokenStore, emailSender);
    }

    @Test
    @DisplayName("유효한 토큰은 한 번 소비하고 회원을 활성화한다")
    void verifyToken() {
        Member member = pendingMember();
        when(tokenStore.consume("verification-token")).thenReturn(Optional.of(1L));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        service.verify("verification-token");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("만료되거나 위조된 토큰은 TOKEN_EXPIRED를 반환한다")
    void rejectsExpiredToken() {
        when(tokenStore.consume("expired-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("expired-token"))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    @Test
    @DisplayName("토큰이 없으면 INVALID_INPUT을 반환한다")
    void rejectsMissingToken() {
        assertThatThrownBy(() -> service.verify(" "))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    private Member pendingMember() {
        Member member = Member.register(
            "artisan@example.com",
            "password-hash",
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

    private EmailVerificationProperties properties() {
        return new EmailVerificationProperties(
            URI.create("http://localhost:8080/api/member/email-verifications/verify"),
            URI.create("http://localhost:3000/"),
            "no-reply@jangingmall.local",
            1800
        );
    }
}
