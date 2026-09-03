package com.jangingmall.backend.global.exception;

public class BusinessRuleViolationException extends BusinessException {

    public BusinessRuleViolationException() {
        super(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
