package com.jangingmall.backend.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.RefundAccount;
import com.jangingmall.backend.payment.domain.RefundAccountRepository;
import com.jangingmall.backend.payment.domain.SavedPaymentMethod;
import com.jangingmall.backend.payment.domain.SavedPaymentMethodRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentProfileServiceTest {

    @Mock private MemberAccess memberAccess;
    @Mock private SavedPaymentMethodRepository paymentMethods;
    @Mock private RefundAccountRepository refundAccounts;
    private PaymentProfileService service;

    @BeforeEach
    void setUp() {
        service = new PaymentProfileService(memberAccess, paymentMethods, refundAccounts);
    }

    @Test
    @DisplayName("PAY-P2-001/002 등록 결제수단 목록과 빈 목록을 조회한다")
    void listsPaymentMethods() {
        SavedPaymentMethod method = method(10L, 1L, true);
        when(paymentMethods.findByMemberIdOrderByIdAsc(1L)).thenReturn(List.of(method));

        assertThat(service.methods(1L)).singleElement().satisfies(data -> {
            assertThat(data.paymentMethodId()).isEqualTo(10L);
            assertThat(data.cardNumberMasked()).doesNotContain("4242424242424242");
            assertThat(data.isDefault()).isTrue();
        });
        when(paymentMethods.findByMemberIdOrderByIdAsc(2L)).thenReturn(List.of());
        assertThat(service.methods(2L)).isEmpty();
    }

    @Test
    @DisplayName("PAY-P2-005 정상 결제수단 등록은 원문 카드번호를 저장하지 않는다")
    void registersPaymentMethod() {
        when(paymentMethods.findByMemberIdAndCardFingerprint(any(), any())).thenReturn(Optional.empty());
        when(paymentMethods.countByMemberId(1L)).thenReturn(0L);
        when(paymentMethods.save(any())).thenAnswer(invocation -> {
            SavedPaymentMethod method = invocation.getArgument(0);
            ReflectionTestUtils.setField(method, "id", 10L);
            return method;
        });

        PaymentProfileService.PaymentMethodData result = service.registerMethod(1L,
            new PaymentProfileService.RegisterPaymentMethod(PaymentMethod.CARD, "4242-4242-4242-4242", "12/30", "900101"));

        assertThat(result.paymentMethodId()).isEqualTo(10L);
        assertThat(result.cardNumberMasked()).isEqualTo("424242******4242");
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("PAY-P2-007 잘못된 카드번호·만료일·본인확인정보는 거부한다")
    void rejectsInvalidPaymentMethod() {
        assertInvalid(() -> service.registerMethod(1L,
            new PaymentProfileService.RegisterPaymentMethod(PaymentMethod.CARD, "1111111111111111", "12/30", "900101")));
        assertInvalid(() -> service.registerMethod(1L,
            new PaymentProfileService.RegisterPaymentMethod(PaymentMethod.CARD, "4242424242424242", "13/30", "900101")));
        assertInvalid(() -> service.registerMethod(1L,
            new PaymentProfileService.RegisterPaymentMethod(PaymentMethod.CARD, "4242424242424242", "12/30", "123")));
    }

    @Test
    @DisplayName("PAY-P2-008 동일 카드 중복 등록은 409 충돌로 차단한다")
    void rejectsDuplicatePaymentMethod() {
        when(paymentMethods.findByMemberIdAndCardFingerprint(any(), any())).thenReturn(Optional.of(method(10L, 1L, true)));

        assertThatThrownBy(() -> service.registerMethod(1L,
            new PaymentProfileService.RegisterPaymentMethod(PaymentMethod.CARD, "4242424242424242", "12/30", "900101")))
            .isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("PAY-P2-010 기본 결제수단 삭제 시 다음 수단을 기본값으로 승격한다")
    void deletesOwnedPaymentMethodAndPromotesNext() {
        SavedPaymentMethod deleted = method(10L, 1L, true);
        SavedPaymentMethod next = method(11L, 1L, false);
        when(paymentMethods.findById(10L)).thenReturn(Optional.of(deleted));
        when(paymentMethods.findByMemberIdOrderByIdAsc(1L)).thenReturn(List.of(next));

        service.deleteMethod(1L, 10L);

        verify(paymentMethods).delete(deleted);
        assertThat(next.isDefaultMethod()).isTrue();
    }

    @Test
    @DisplayName("PAY-P2-011/012 없거나 다른 회원의 결제수단 삭제는 404로 감춘다")
    void rejectsUnknownOrForeignPaymentMethod() {
        when(paymentMethods.findById(10L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.deleteMethod(1L, 10L));
        when(paymentMethods.findById(11L)).thenReturn(Optional.of(method(11L, 2L, false)));
        assertNotFound(() -> service.deleteMethod(1L, 11L));
    }

    @Test
    @DisplayName("PAY-P2-014 정상 환불계좌 등록은 계좌번호를 마스킹한다")
    void registersRefundAccount() {
        when(refundAccounts.save(any())).thenAnswer(invocation -> {
            RefundAccount account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 20L);
            return account;
        });

        PaymentProfileService.RefundAccountData result = service.registerRefundAccount(1L,
            new PaymentProfileService.RegisterRefundAccount("국민은행", "123-456-789012", "홍길동"));

        assertThat(result.refundAccountId()).isEqualTo(20L);
        assertThat(result.accountNumberMasked()).isEqualTo("********9012");
        assertThat(result.accountNumberMasked()).endsWith("9012").doesNotContain("123456789012");
    }

    @Test
    @DisplayName("PAY-P2-016 형식이 잘못된 환불계좌는 400으로 거부한다")
    void rejectsInvalidRefundAccount() {
        assertInvalid(() -> service.registerRefundAccount(1L,
            new PaymentProfileService.RegisterRefundAccount("!", "123", "1")));
    }

    private SavedPaymentMethod method(Long id, Long memberId, boolean defaultMethod) {
        SavedPaymentMethod method = new SavedPaymentMethod(memberId, PaymentMethod.CARD, "VISA",
            "424242******4242", "fingerprint-" + id, defaultMethod);
        ReflectionTestUtils.setField(method, "id", id);
        return method;
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
