package com.jangingmall.backend.member.domain;

final class MemberErrorMessage {
    static final String WITHDRAWN = "탈퇴 회원은 활성화할 수 없습니다";
    static final String ARTISAN_TRANSITION = "활성 일반 회원만 장인으로 승인할 수 있습니다";
    static final String ALREADY_REVIEWED = "이미 심사한 신청입니다";
    static final String REQUIRED_AGREEMENTS = "필수 약관에 동의해야 합니다";
    private MemberErrorMessage() {}
}
