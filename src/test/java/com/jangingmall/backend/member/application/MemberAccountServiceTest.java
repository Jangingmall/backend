package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRole;
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
class MemberAccountServiceTest {

    @Mock private MemberAccess access;
    @Mock private PasswordEncoder passwords;
    @Mock private RefreshTokenStore refreshTokens;
    private MemberAccountService accounts;

    @BeforeEach
    void setUp() {
        accounts = new MemberAccountService(access, passwords, refreshTokens);
    }

    @Test
    @DisplayName("회원은 입력한 항목만 자신의 프로필에 반영한다")
    void updateProfile() {
        Member member = activeMember();
        when(access.lock(1L)).thenReturn(member);

        MemberProfile profile = accounts.update(1L, Optional.empty(), Optional.of("도공이"), Optional.of("01099998888"));

        assertThat(profile.name()).isEqualTo("김도공");
        assertThat(profile.nickname()).isEqualTo("도공이");
        assertThat(profile.profileImageUrl()).isNull();
        assertThat(member.getPhone()).isEqualTo("01099998888");
    }

    @Test
    @DisplayName("현재 비밀번호가 일치할 때만 비밀번호를 바꾸고 모든 Refresh Token을 무효화한다")
    void changePassword() {
        Member member = activeMember();
        when(access.lock(1L)).thenReturn(member);
        when(passwords.matches("before-password", "hashed-password")).thenReturn(true);
        when(passwords.encode("after-password")).thenReturn("new-hash");

        accounts.changePassword(1L, "before-password", "after-password");

        assertThat(member.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokens).delete(1L);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 기존 세션을 유지한 채 UNAUTHORIZED를 반환한다")
    void rejectsIncorrectCurrentPassword() {
        Member member = activeMember();
        when(access.lock(1L)).thenReturn(member);
        when(passwords.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> accounts.changePassword(1L, "wrong-password", "after-password"))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(member.getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    @DisplayName("회원 탈퇴는 WITHDRAWN으로 전환하고 Refresh Token을 무효화한다")
    void withdraw() {
        Member member = activeMember();
        when(access.lock(1L)).thenReturn(member);

        accounts.withdraw(1L, "더 이상 이용하지 않습니다");

        assertThat(member.getStatus().name()).isEqualTo("WITHDRAWN");
        assertThat(member.getWithdrawalReason()).isEqualTo("더 이상 이용하지 않습니다");
        verify(refreshTokens).delete(1L);
    }

    private Member activeMember() {
        Member member = Member.register("artisan@example.com", "hashed-password", "김도공", "01012345678",
            MemberRole.USER, true, true, true, false);
        member.activate();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
