package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/member") @RequiredArgsConstructor
public class MemberActivityController {
    private final MemberActivityService activities;

    @GetMapping("/settings")
    public MemberActivityService.Settings settings(@AuthenticationPrincipal Long memberId) {
        return activities.settings(memberId);
    }

    @PatchMapping("/settings")
    public MemberActivityService.Settings updateSettings(@AuthenticationPrincipal Long memberId, @Valid @RequestBody Settings request) {
        return activities.updateSettings(memberId, Optional.ofNullable(request.darkMode()), Optional.ofNullable(request.marketing()));
    }

    @PostMapping("/recent-views")
    public ApiResponse<Void> record(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RecentView request) {
        activities.recordView(memberId, request.productId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/recent-views/merge")
    public ApiResponse<Void> merge(@AuthenticationPrincipal Long memberId, @Valid @RequestBody RecentViews request) {
        activities.mergeViews(memberId, request.productIds());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/recent-views")
    public ApiResponse<Void> clear(@AuthenticationPrincipal Long memberId) {
        activities.clearViews(memberId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/recent-views")
    public CursorPage<Map<String, Object>> recent(@AuthenticationPrincipal Long memberId,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return activities.recentViews(memberId, cursor, limit);
    }

    @PostMapping("/artisans/{artisanId}/subscribe")
    public ApiResponse<Void> subscribe(@AuthenticationPrincipal Long memberId, @PathVariable Long artisanId) {
        activities.subscribe(memberId, artisanId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/artisans/{artisanId}/subscribe")
    public ApiResponse<Void> unsubscribe(@AuthenticationPrincipal Long memberId, @PathVariable Long artisanId) {
        activities.unsubscribe(memberId, artisanId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/artisans/subscriptions")
    public CursorPage<Map<String, Object>> subscriptions(@AuthenticationPrincipal Long memberId,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return activities.subscriptions(memberId, PageRequest.from(cursor, limit));
    }

    @PatchMapping("/artisans/subscriptions/notifications")
    public ApiResponse<Void> notifications(@AuthenticationPrincipal Long memberId, @Valid @RequestBody Notifications request) {
        activities.notifications(memberId, request.notificationsEnabled());
        return ApiResponse.ok(null);
    }

    public record Settings(Boolean darkMode, Boolean marketing) {}
    public record RecentView(@NotNull @Positive Long productId) {}
    public record RecentViews(@NotNull @Size(max = 100) List<@NotNull @Positive Long> productIds) {}
    public record Notifications(@NotNull Boolean notificationsEnabled) {}
}
