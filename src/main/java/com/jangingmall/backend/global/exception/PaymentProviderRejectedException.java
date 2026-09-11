package com.jangingmall.backend.global.exception;

/**
 * 결제사가 요청을 명시적으로 거절한 경우다. 네트워크 단절처럼 결과가 불명확한 오류와 구분해
 * 로컬 결제 실패 상태를 안전하게 확정할 때 사용한다.
 */
public class PaymentProviderRejectedException extends BusinessException {

    private final String providerCode;

    public PaymentProviderRejectedException(String providerCode, String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
        this.providerCode = providerCode;
    }

    public String providerCode() {
        return providerCode;
    }
}
