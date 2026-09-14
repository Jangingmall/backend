package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.member.application.*;
import com.jangingmall.backend.member.domain.SellerApplication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SellerApplicationController {
    private final SellerApplicationService applications;

    @PostMapping("/api/member/artisans/applications")
    public ResponseEntity<SellerApplicationData> apply(@AuthenticationPrincipal Long memberId,
                                                      @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(201).body(applications.apply(memberId, request.businessName(),
            request.introduction(), request.businessLicenseImageUrl()));
    }

    @GetMapping("/api/member/artisans/applications/me")
    public SellerApplicationData mine(@AuthenticationPrincipal Long memberId) {
        return applications.mine(memberId);
    }

    @GetMapping("/api/admin/seller-applications")
    public CursorPage<SellerApplicationData> list(@AuthenticationPrincipal Long memberId,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int limit,
                                                 @RequestParam(required = false) String status) {
        return applications.list(memberId, PageRequest.from(cursor, limit), status);
    }

    @GetMapping("/api/admin/seller-applications/{applicationId}")
    public SellerApplicationData detail(@AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return applications.detail(memberId, applicationId);
    }

    @PostMapping("/api/admin/seller-applications/{applicationId}/approve")
    public SellerApplicationData approve(@AuthenticationPrincipal Long memberId, @PathVariable Long applicationId) {
        return applications.approve(memberId, applicationId);
    }

    @PostMapping("/api/admin/seller-applications/{applicationId}/reject")
    public SellerApplicationData reject(@AuthenticationPrincipal Long memberId, @PathVariable Long applicationId,
                                        @Valid @RequestBody Rejection request) {
        return applications.reject(memberId, applicationId, request.reason());
    }

    @PatchMapping("/api/admin/artisans/applications/{applicationId}/pipeline")
    public SellerApplicationPipelineResult updatePipeline(@AuthenticationPrincipal Long memberId, @PathVariable Long applicationId,
                                                           @Valid @RequestBody Pipeline request) {
        return applications.updatePipeline(memberId, applicationId, request.step(), request.status(), request.qualificationTier());
    }

    public record ApplicationRequest(@NotBlank @Size(max = 100) String businessName,
                                     @NotBlank @Size(max = 255) String introduction,
                                     @NotBlank @Size(max = 500) @Pattern(regexp = "https://[^\\s]+") String businessLicenseImageUrl) {}
    public record Rejection(@NotBlank @Size(max = 65535) String reason) {}
    public record Pipeline(@NotNull SellerApplication.Step step, @NotNull SellerApplication.Stage status,
                           SellerApplication.Qualification qualificationTier) {}
}
