package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.RefundAccount;
import com.jangingmall.backend.payment.domain.RefundAccountRepository;
import com.jangingmall.backend.payment.domain.SavedPaymentMethod;
import com.jangingmall.backend.payment.domain.SavedPaymentMethodRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentProfileService {

    private final MemberAccess memberAccess;
    private final SavedPaymentMethodRepository paymentMethods;
    private final RefundAccountRepository refundAccounts;

    @Transactional(readOnly = true)
    public List<PaymentMethodData> methods(Long memberId) {
        memberAccess.active(memberId);
        return paymentMethods.findByMemberIdOrderByIdAsc(memberId).stream().map(PaymentMethodData::from).toList();
    }

    @Transactional
    public PaymentMethodData registerMethod(Long memberId, RegisterPaymentMethod command) {
        memberAccess.active(memberId);
        if (command.type() != PaymentMethod.CARD) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        String cardNumber = digits(command.cardNumber());
        validateCardNumber(cardNumber);
        validateExpiry(command.expiry());
        String identity = digits(command.birthOrBusinessNo());
        if (identity.length() != 6 && identity.length() != 10) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        String fingerprint = fingerprint(cardNumber);
        if (paymentMethods.findByMemberIdAndCardFingerprint(memberId, fingerprint).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        SavedPaymentMethod saved = paymentMethods.save(new SavedPaymentMethod(memberId, command.type(),
            cardCompany(cardNumber), mask(cardNumber, 6, 4), fingerprint, paymentMethods.countByMemberId(memberId) == 0));
        return PaymentMethodData.from(saved);
    }

    @Transactional
    public void deleteMethod(Long memberId, Long paymentMethodId) {
        memberAccess.active(memberId);
        SavedPaymentMethod method = paymentMethods.findById(paymentMethodId)
            .filter(candidate -> candidate.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        boolean wasDefault = method.isDefaultMethod();
        paymentMethods.delete(method);
        if (wasDefault) {
            paymentMethods.findByMemberIdOrderByIdAsc(memberId).stream().findFirst()
                .ifPresent(SavedPaymentMethod::makeDefault);
        }
    }

    @Transactional
    public RefundAccountData registerRefundAccount(Long memberId, RegisterRefundAccount command) {
        memberAccess.active(memberId);
        String bankName = normalizedName(command.bankName());
        String holder = normalizedName(command.accountHolder());
        String accountNumber = digits(command.accountNumber());
        if (bankName.length() < 2 || bankName.length() > 50 || holder.length() < 2 || holder.length() > 50
            || accountNumber.length() < 8 || accountNumber.length() > 20) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        RefundAccount saved = refundAccounts.save(new RefundAccount(memberId, bankName,
            mask(accountNumber, 0, 4), fingerprint(accountNumber), holder));
        return RefundAccountData.from(saved);
    }

    private void validateCardNumber(String cardNumber) {
        if (cardNumber.length() < 13 || cardNumber.length() > 19) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';
            if (doubleDigit && (digit *= 2) > 9) digit -= 9;
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        if (sum % 10 != 0) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateExpiry(String expiry) {
        String value = expiry == null ? "" : expiry.replace("/", "").trim();
        if (!value.matches("\\d{4}")) throw new DomainException(ErrorCode.INVALID_INPUT);
        int month = Integer.parseInt(value.substring(0, 2));
        int year = 2000 + Integer.parseInt(value.substring(2));
        if (month < 1 || month > 12 || YearMonth.of(year, month).isBefore(YearMonth.now())) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private String digits(String value) {
        if (value == null || !value.matches("[0-9 -]+")) throw new DomainException(ErrorCode.INVALID_INPUT);
        return value.replaceAll("[^0-9]", "");
    }

    private String normalizedName(String value) {
        if (value == null || !value.trim().matches("[가-힣A-Za-z ]+")) throw new DomainException(ErrorCode.INVALID_INPUT);
        return value.trim().replaceAll("\\s+", " ");
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String mask(String value, int visiblePrefix, int visibleSuffix) {
        int hidden = value.length() - visiblePrefix - visibleSuffix;
        return value.substring(0, visiblePrefix) + "*".repeat(Math.max(0, hidden))
            + value.substring(value.length() - visibleSuffix);
    }

    private String cardCompany(String cardNumber) {
        // 카드 앞자리만으로 발급사를 오판하지 않는다. 실제 발급사 코드는 빌링키 연동 응답으로 보강한다.
        return "UNKNOWN";
    }

    public record RegisterPaymentMethod(PaymentMethod type, String cardNumber, String expiry,
                                        String birthOrBusinessNo) {}
    public record RegisterRefundAccount(String bankName, String accountNumber, String accountHolder) {}

    public record PaymentMethodData(Long paymentMethodId, PaymentMethod type, String cardCompany,
                                    String cardNumberMasked, boolean isDefault) {
        static PaymentMethodData from(SavedPaymentMethod method) {
            return new PaymentMethodData(method.getId(), method.getType(), method.getCardCompany(),
                method.getCardNumberMasked(), method.isDefaultMethod());
        }
    }

    public record RefundAccountData(Long refundAccountId, String bankName, String accountNumberMasked,
                                    String accountHolder) {
        static RefundAccountData from(RefundAccount account) {
            return new RefundAccountData(account.getId(), account.getBankName(), account.getAccountNumberMasked(),
                account.getAccountHolder());
        }
    }
}
