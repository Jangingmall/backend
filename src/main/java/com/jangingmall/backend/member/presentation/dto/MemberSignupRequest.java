package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.MemberSignupCommand;
import com.jangingmall.backend.member.domain.MemberRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberSignupRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다") String password,
    @NotBlank String passwordConfirm,
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Pattern(regexp = "\\d{9,20}") String phone,
    @NotNull MemberRole role,
    @NotNull @Valid Agreements agreements
) {

    public MemberSignupCommand toCommand() {
        return new MemberSignupCommand(
            email,
            password,
            passwordConfirm,
            name,
            phone,
            role,
            agreements.age14OrOlder(),
            agreements.termsOfService(),
            agreements.privacyCollection(),
            Boolean.TRUE.equals(agreements.marketing())
        );
    }

    public record Agreements(
        @NotNull Boolean age14OrOlder,
        @NotNull Boolean termsOfService,
        @NotNull Boolean privacyCollection,
        Boolean marketing
    ) {
    }
}
