package com.jangingmall.backend.member.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmailVerificationRequest {

    public record Send(
        @NotBlank @Email @Size(max = 255) String email
    ) {}

    public record Verify(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "인증 코드는 6자리 숫자입니다") String code
    ) {}
}
