package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.GenerationCommand;
import com.jangingmall.backend.content.application.GenerationResponse;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/content/products/{productId}/generations")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<GenerationResponse>> request(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody GenerationRequest.Create request
    ) {
        GenerationCommand.Request command = new GenerationCommand.Request(
            productId,
            memberId,
            request.images(),
            request.productName(),
            request.howMade(),
            request.careTips()
        );
        return ResponseEntity.status(202).body(ApiResponse.accepted(generationService.request(command)));
    }

    @GetMapping("/{generationId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<GenerationResponse> poll(
        @PathVariable Long productId,
        @PathVariable Long generationId,
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(generationService.poll(productId, generationId, memberId));
    }
}
