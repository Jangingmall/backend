package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.MemberService;
import com.jangingmall.backend.member.application.MemberAuthenticationService;
import com.jangingmall.backend.member.application.EmailVerificationProperties;
import com.jangingmall.backend.member.application.EmailVerificationService;
import com.jangingmall.backend.member.application.MemberSession;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.member.presentation.dto.MemberLoginRequest;
import com.jangingmall.backend.member.presentation.dto.MemberLoginResponse;
import com.jangingmall.backend.member.presentation.dto.MemberProfileResponse;
import com.jangingmall.backend.member.presentation.dto.EmailVerificationRequest;
import com.jangingmall.backend.member.presentation.dto.EmailVerificationResponse;
import com.jangingmall.backend.member.presentation.dto.MemberTokenRefreshResponse;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import com.jangingmall.backend.member.presentation.dto.MemberSignupResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberAuthenticationService memberAuthenticationService;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationProperties emailVerificationProperties;
    private final JwtProperties jwtProperties;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberSignupResponse>> signUp(
        @Valid @RequestBody MemberSignupRequest request
    ) {
        MemberSignupResponse response = MemberSignupResponse.from(memberService.signUp(request.toCommand()));
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberLoginResponse>> login(
        @Valid @RequestBody MemberLoginRequest request
    ) {
        MemberSession session = memberAuthenticationService.login(request.email(), request.password());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
            .body(ApiResponse.ok(MemberLoginResponse.from(session)));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<MemberTokenRefreshResponse>> refresh(
        @CookieValue(value = "refreshToken", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        MemberSession session = memberAuthenticationService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
            .body(ApiResponse.ok(MemberTokenRefreshResponse.from(session, jwtProperties)));
    }

    @PostMapping("/email-verifications")
    public ApiResponse<EmailVerificationResponse> resendVerificationEmail(
        @Valid @RequestBody EmailVerificationRequest request
    ) {
        long expiresInSeconds = emailVerificationService.sendVerification(request.email());
        return ApiResponse.ok(new EmailVerificationResponse(expiresInSeconds));
    }

    @GetMapping("/email-verifications/verify")
    public ResponseEntity<Void> verifyEmail(@RequestParam(required = false) String token) {
        try {
            emailVerificationService.verify(token);
        } catch (DomainException ignored) {
            // The API contract requires the browser flow to return to the frontend even on failure.
        }
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(emailVerificationProperties.successRedirectUrl())
            .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Long memberId) {
        memberAuthenticationService.logout(memberId);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
            .body(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ApiResponse<MemberProfileResponse> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(MemberProfileResponse.from(memberAuthenticationService.getProfile(memberId)));
    }

    private ResponseCookie refreshCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(jwtProperties.refreshCookieSecure())
            .sameSite("Strict")
            .path("/api/member")
            .maxAge(Duration.ofDays(7))
            .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(jwtProperties.refreshCookieSecure())
            .sameSite("Strict")
            .path("/api/member")
            .maxAge(Duration.ZERO)
            .build();
    }
}
