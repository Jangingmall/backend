package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.member.application.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/member/me") @RequiredArgsConstructor
public class MemberQueryController {
    private final MemberQueryService queries;

    @GetMapping("/wishes")
    public CursorPage<Map<String, Object>> wishes(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return queries.wishes(memberId, PageRequest.from(cursor, limit));
    }

    @GetMapping("/orders")
    public CursorPage<Map<String, Object>> orders(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "ALL") String status) {
        return queries.orders(memberId, PageRequest.from(cursor, limit), status);
    }

    @GetMapping("/orders/{orderId}")
    public Map<String, Object> order(@AuthenticationPrincipal Long memberId, @PathVariable Long orderId) {
        return queries.order(memberId, orderId);
    }

    @GetMapping("/reviews")
    public CursorPage<Map<String, Object>> reviews(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return queries.reviews(memberId, PageRequest.from(cursor, limit), false);
    }

    @GetMapping("/reviews/writable")
    public CursorPage<Map<String, Object>> writable(@AuthenticationPrincipal Long memberId,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return queries.reviews(memberId, PageRequest.from(cursor, limit), true);
    }
}
