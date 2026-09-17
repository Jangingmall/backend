package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.ContentCommand;
import com.jangingmall.backend.content.application.ContentResponse;
import com.jangingmall.backend.content.application.ContentService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content/products/{productId}")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/contents")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.Detail> getContent(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(contentService.getContent(productId, memberId));
    }

    @GetMapping("/contents/versions")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<List<ContentResponse.VersionHistory>> getVersionHistory(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(contentService.getVersionHistory(productId, memberId));
    }

    @PostMapping("/contents/{contentId}/submit")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.StatusChanged> submitForReview(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @AuthenticationPrincipal Long memberId
    ) {
        ContentCommand.SubmitForReview command = new ContentCommand.SubmitForReview(productId, contentId, memberId);
        return ApiResponse.ok(contentService.submitForReview(command));
    }

    @PostMapping("/contents/{contentId}/approve")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.StatusChanged> approve(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ContentRequest.Approve request
    ) {
        ContentCommand.Approve command = new ContentCommand.Approve(
            productId, contentId, memberId,
            request.factCheckConfirmed(), request.photoMatchConfirmed(), request.displayApprovalBadge()
        );
        return ApiResponse.ok(contentService.approve(command));
    }

    @PostMapping("/contents/{contentId}/reject")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.StatusChanged> reject(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @AuthenticationPrincipal Long memberId
    ) {
        ContentCommand.Reject command = new ContentCommand.Reject(productId, contentId, memberId);
        return ApiResponse.ok(contentService.reject(command));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.StatusChanged> publish(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId
    ) {
        ContentCommand.Publish command = new ContentCommand.Publish(productId, memberId);
        return ApiResponse.ok(contentService.publish(command));
    }

    @PatchMapping("/contents/{contentId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.Detail> replaceBlocks(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ContentRequest.ReplaceBlocks request
    ) {
        List<ContentCommand.ContentBlockInput> blocks = request.blocks().stream()
            .map(block -> new ContentCommand.ContentBlockInput(block.order(), block.tag(), block.hasImage(),
                block.imageUrl(), block.videoUrl(), block.text()))
            .toList();
        return ApiResponse.ok(contentService.replaceBlocks(
            new ContentCommand.ReplaceBlocks(productId, contentId, memberId, blocks)));
    }

    @PatchMapping("/contents/{contentId}/blocks/{blockOrder}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.BlockChanged> updateBlock(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @PathVariable int blockOrder,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ContentRequest.UpdateBlock request
    ) {
        ContentCommand.ContentBlockInput block = new ContentCommand.ContentBlockInput(
            blockOrder, request.tag(), request.hasImage(), request.imageUrl(), request.videoUrl(), request.text());
        return ApiResponse.ok(contentService.updateBlock(
            new ContentCommand.UpdateBlock(productId, contentId, blockOrder, memberId, block)));
    }
}
