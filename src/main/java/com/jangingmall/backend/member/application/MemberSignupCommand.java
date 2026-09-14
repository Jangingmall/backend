package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.MemberRole;

public record MemberSignupCommand(
    String email,
    String password,
    String passwordConfirm,
    String name,
    String phone,
    MemberRole role,
    boolean age14OrOlder,
    boolean termsOfService,
    boolean privacyCollection,
    boolean marketing
) {
}
