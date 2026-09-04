package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Member> memberCaptor;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("정상 회원가입 시 비밀번호를 해시 처리하고 이메일 인증 대기 회원을 저장한다")
    void signUp() {
        MemberSignupCommand command = validCommand();
        when(memberRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("hashed-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        MemberSignupResult result = memberService.signUp(command);

        verify(memberRepository).save(memberCaptor.capture());
        Member savedMember = memberCaptor.getValue();
        assertThat(savedMember.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedMember.getStatus().name()).isEqualTo("PENDING_VERIFICATION");
        assertThat(savedMember.isMarketingAgreed()).isTrue();
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo(command.email());
        verify(eventPublisher).publishEvent(any(MemberRegisteredEvent.class));
    }

    @Test
    @DisplayName("마케팅 미동의도 회원가입을 막지 않는다")
    void signUpWithoutMarketingAgreement() {
        MemberSignupCommand command = new MemberSignupCommand(
            "artisan@example.com", "password", "password", "김도공", "01012345678", MemberRole.USER,
            true, true, true, false
        );
        when(memberRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("hashed-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memberService.signUp(command);

        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().isMarketingAgreed()).isFalse();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 CONFLICT를 반환한다")
    void signUpWithDuplicateEmail() {
        MemberSignupCommand command = validCommand();
        when(memberRepository.existsByEmail(command.email())).thenReturn(true);

        assertThatThrownBy(() -> memberService.signUp(command))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("공개 회원가입으로는 ARTISAN 또는 ADMIN 역할을 만들 수 없다")
    void signUpWithPrivilegedRole() {
        MemberSignupCommand command = new MemberSignupCommand(
            "artisan@example.com", "password", "password", "김도공", "01012345678", MemberRole.ADMIN,
            true, true, true, false
        );

        assertThatThrownBy(() -> memberService.signUp(command))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("비밀번호 확인이 일치하지 않으면 INVALID_INPUT을 반환한다")
    void signUpWithDifferentPasswordConfirmation() {
        MemberSignupCommand command = new MemberSignupCommand(
            "artisan@example.com", "password", "different-password", "김도공", "01012345678",
            MemberRole.USER, true, true, true, true
        );

        assertThatThrownBy(() -> memberService.signUp(command))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("필수 약관 중 하나라도 동의하지 않으면 BUSINESS_RULE_VIOLATION을 반환한다")
    void signUpWithoutRequiredAgreements() {
        MemberSignupCommand command = new MemberSignupCommand(
            "artisan@example.com", "password", "password", "김도공", "01012345678", MemberRole.USER,
            false, true, true, false
        );

        assertThatThrownBy(() -> memberService.signUp(command))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    private MemberSignupCommand validCommand() {
        return new MemberSignupCommand(
            "artisan@example.com", "password", "password", "김도공", "01012345678", MemberRole.USER,
            true, true, true, true
        );
    }

}
