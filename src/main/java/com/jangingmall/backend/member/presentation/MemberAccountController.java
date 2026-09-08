package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.MemberAccountService;
import com.jangingmall.backend.member.presentation.dto.MemberAccountRequests;
import com.jangingmall.backend.member.presentation.dto.MemberProfileResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member/me")
@RequiredArgsConstructor
public class MemberAccountController {
    private final MemberAccountService accounts;

    @PatchMapping
    public MemberProfileResponse update(@AuthenticationPrincipal Long memberId,
                                        @Valid @RequestBody MemberAccountRequests.Profile request) {
        return MemberProfileResponse.from(accounts.update(memberId, Optional.ofNullable(request.name()),
            Optional.ofNullable(request.nickname()), Optional.ofNullable(request.phone())));
    }

    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Long memberId,
                                           @Valid @RequestBody MemberAccountRequests.Password request) {
        accounts.changePassword(memberId, request.currentPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long memberId,
                                     @Valid @RequestBody MemberAccountRequests.Withdrawal request) {
        accounts.withdraw(memberId, request.reason());
        return ApiResponse.ok(null);
    }
}
