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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PatchMapping("/contents/{contentId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.Detail> bulkUpdateBlocks(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ContentRequest.BulkUpdate request
    ) {
        List<ContentCommand.BlockInput> blockInputs = request.blocks().stream()
            .map(b -> new ContentCommand.BlockInput(b.order(), b.tag(), b.text(), b.imageUrl(), b.videoUrl()))
            .toList();
        ContentCommand.BulkUpdate command = new ContentCommand.BulkUpdate(productId, contentId, memberId, blockInputs);
        return ApiResponse.ok(contentService.bulkUpdateBlocks(command));
    }

    @PatchMapping("/contents/{contentId}/blocks/{blockOrder}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ContentResponse.BlockEdit> updateBlock(
        @PathVariable Long productId,
        @PathVariable Long contentId,
        @PathVariable int blockOrder,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ContentRequest.BlockUpdate request
    ) {
        ContentCommand.BlockUpdate command = new ContentCommand.BlockUpdate(
            productId, contentId, blockOrder, memberId, request.tag(), request.text(), request.imageUrl()
        );
        return ApiResponse.ok(contentService.updateBlock(command));
    }

    @GetMapping("/contents/versions")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<List<ContentResponse.VersionHistory>> getVersionHistory(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(contentService.getVersionHistory(productId, memberId));
    }
}
