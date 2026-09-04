package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.MemberService;
import com.jangingmall.backend.member.application.MemberAuthenticationService;
import com.jangingmall.backend.member.application.MemberSession;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.presentation.dto.MemberLoginRequest;
import com.jangingmall.backend.member.presentation.dto.MemberLoginResponse;
import com.jangingmall.backend.member.presentation.dto.MemberProfileResponse;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import com.jangingmall.backend.member.presentation.dto.MemberSignupResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MemberAuthenticationService memberAuthenticationService;

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
    public ResponseEntity<ApiResponse<MemberLoginResponse>> refresh(
        @CookieValue(value = "refreshToken", required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DomainException(ErrorCode.UNAUTHORIZED);
        }
        MemberSession session = memberAuthenticationService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
            .body(ApiResponse.ok(MemberLoginResponse.from(session)));
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
            .sameSite("Strict")
            .path("/api/member")
            .maxAge(Duration.ofDays(7))
            .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .sameSite("Strict")
            .path("/api/member")
            .maxAge(Duration.ZERO)
            .build();
    }
}
