package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.MemberQueryService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ApiResponse<Page<Map<String, Object>>> wishes(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(queries.wishes(memberId, pageable));
    }

    @GetMapping("/wishes/{productId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> isWished(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId
    ) {
        return queries.isWished(memberId, productId)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    @GetMapping("/orders/summary")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Object>> orderSummary(
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(queries.orderCountSummary(memberId));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Page<Map<String, Object>>> orders(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String artisanName
    ) {
        return ApiResponse.ok(queries.orders(memberId, pageable, status, from, to, artisanName));
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Map<String, Object>> order(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long orderId
    ) {
        return ApiResponse.ok(queries.order(memberId, orderId));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Page<Map<String, Object>>> reviews(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(queries.reviews(memberId, pageable, false));
    }

    @GetMapping("/reviews/writable")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Page<Map<String, Object>>> writable(
        @AuthenticationPrincipal Long memberId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(queries.reviews(memberId, pageable, true));
    }
}
