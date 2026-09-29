package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.ContentService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ai/products")
@RequiredArgsConstructor
public class AiSyncController {

    private final ContentService contentService;

    @PostMapping("/bulk-sync")
    public ResponseEntity<ApiResponse<BulkSyncResult>> bulkSync() {
        int synced = contentService.bulkSyncPublishedProductsToAi();
        return ResponseEntity.ok(ApiResponse.ok(new BulkSyncResult(synced)));
    }

    public record BulkSyncResult(int synced) {}
}
