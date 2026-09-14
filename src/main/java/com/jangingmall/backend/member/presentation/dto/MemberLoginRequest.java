package com.jangingmall.backend.member.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberLoginRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank String password
) {
}
