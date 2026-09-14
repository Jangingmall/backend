package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.InterviewCommand;
import com.jangingmall.backend.content.application.InterviewResponse;
import com.jangingmall.backend.content.application.InterviewService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content/products/{productId}/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<InterviewResponse>> create(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody InterviewRequest.Create request
    ) {
        InterviewCommand.Create command = new InterviewCommand.Create(
            productId,
            memberId,
            request.process(),
            request.materials(),
            request.technique(),
            request.story()
        );
        return ResponseEntity.status(201).body(ApiResponse.created(interviewService.create(command)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<InterviewResponse> find(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(interviewService.find(productId, memberId));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<InterviewResponse> update(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody InterviewRequest.Update request
    ) {
        InterviewCommand.Update command = new InterviewCommand.Update(
            productId,
            memberId,
            request.process(),
            request.materials(),
            request.technique(),
            request.story()
        );
        return ApiResponse.ok(interviewService.update(command));
    }
}
