package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.CursorPage;
import com.jangingmall.backend.member.application.MemberQueryService;
import com.jangingmall.backend.member.application.PageRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member/me")
@RequiredArgsConstructor
public class MemberQueryController {

    private final MemberQueryService queries;

    @GetMapping("/wishes")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<CursorPage<Map<String, Object>>> wishes(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(queries.wishes(memberId, PageRequest.from(cursor, limit)));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<CursorPage<Map<String, Object>>> orders(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "ALL") String status
    ) {
        return ApiResponse.ok(queries.orders(memberId, PageRequest.from(cursor, limit), status));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Object>> order(
        @AuthenticationPrincipal Long memberId,
        @org.springframework.web.bind.annotation.PathVariable Long orderId
    ) {
        return ApiResponse.ok(queries.order(memberId, orderId));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<CursorPage<Map<String, Object>>> reviews(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(queries.reviews(memberId, PageRequest.from(cursor, limit), false));
    }

    @GetMapping("/reviews/writable")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<CursorPage<Map<String, Object>>> writable(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(queries.reviews(memberId, PageRequest.from(cursor, limit), true));
    }
}
