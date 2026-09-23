package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationStore verificationStore;
    @Mock private EmailSender emailSender;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(verificationStore, emailSender);
    }

    @Test
    @DisplayName("MEM-P2-001 이메일 인증 코드 발송 시 저장소와 발송기를 호출한다")
    void sendsCodeAndStoresIt() {
        service.sendCode("user@example.com");

        verify(verificationStore).save(eq("user@example.com"), any(), any());
        verify(emailSender).sendVerificationCode(eq("user@example.com"), any());
    }

    @Test
    @DisplayName("MEM-P2-002 이메일 인증 코드가 일치하면 검증 성공한다")
    void verifyWithCorrectCode() {
        when(verificationStore.consumeCode("user@example.com")).thenReturn(Optional.of("123456"));

        service.verify("user@example.com", "123456");
    }

    @Test
    @DisplayName("MEM-P2-003 이메일 인증 코드가 만료되면 410 RESOURCE_EXPIRED를 반환한다")
    void verifyWithExpiredCode() {
        when(verificationStore.consumeCode("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("user@example.com", "123456"))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_EXPIRED));
    }

    @Test
    @DisplayName("MEM-P2-004 이메일 인증 코드가 틀리면 400 INVALID_INPUT을 반환한다")
    void verifyWithWrongCode() {
        when(verificationStore.consumeCode("user@example.com")).thenReturn(Optional.of("999999"));

        assertThatThrownBy(() -> service.verify("user@example.com", "123456"))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> org.assertj.core.api.Assertions.assertThat(ex.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("MEM-P2-005 이메일은 대소문자 구분 없이 정규화하여 코드를 저장한다")
    void normalizesEmailCase() {
        service.sendCode("USER@EXAMPLE.COM");

        verify(verificationStore).save(eq("user@example.com"), any(), any());
        verify(emailSender).sendVerificationCode(eq("user@example.com"), any());
    }
}
