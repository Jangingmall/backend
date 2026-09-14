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
import com.jangingmall.backend.member.domain.MemberSocialAccount;
import com.jangingmall.backend.member.domain.MemberSocialAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OAuthMemberServiceTest {

    @Mock private MemberRepository members;
    @Mock private MemberSocialAccountRepository accounts;
    @Mock private OneTimeTokenStore tokens;
    @Mock private ObjectMapper json;
    private OAuthMemberService service;

    @BeforeEach
    void setUp() {
        service = new OAuthMemberService(members, accounts, tokens, json);
    }

    @Test
    @DisplayName("소셜 추가정보를 마치면 활성 USER와 소셜 계정을 함께 저장한다")
    void completesNewSocialMember() {
        OAuthIdentity identity = new OAuthIdentity("kakao", "provider-subject", "social@example.com");
        when(tokens.consume("oauth-onboarding", "onboarding-token")).thenReturn(Optional.of("identity-payload"));
        when(json.readValue("identity-payload", OAuthIdentity.class)).thenReturn(identity);
        when(members.existsByEmail("social@example.com")).thenReturn(false);
        when(members.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 7L);
            return member;
        });

        MemberSignupResult result = service.complete("onboarding-token",
            new OAuthMemberService.Completion("김도공", "01012345678", true, true, true, false));

        assertThat(result.memberId()).isEqualTo(7L);
        assertThat(result.email()).isEqualTo("social@example.com");
        assertThat(result.status().name()).isEqualTo("ACTIVE");
        ArgumentCaptor<MemberSocialAccount> account = ArgumentCaptor.forClass(MemberSocialAccount.class);
        verify(accounts).save(account.capture());
        assertThat(account.getValue().getMemberId()).isEqualTo(7L);
        assertThat(account.getValue().getRegistrationId()).isEqualTo("kakao");
    }

    @Test
    @DisplayName("필수 약관 동의가 빠진 소셜 추가정보 요청은 토큰을 소비하지 않는다")
    void rejectsMissingAgreementsBeforeConsumingToken() {
        assertThatThrownBy(() -> service.complete("onboarding-token",
            new OAuthMemberService.Completion("김도공", "01012345678", false, true, true, false)))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    @DisplayName("소셜 식별자는 허용한 제공자와 검증된 이메일 형식만 가진다")
    void rejectsInvalidIdentity() {
        assertThatThrownBy(() -> new OAuthIdentity(null, "subject", "social@example.com"))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
