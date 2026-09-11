package com.jangingmall.backend.image.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUploadCleanupScheduler {

    private final ImageService images;

    @Scheduled(fixedDelayString = "${image.storage.cleanup-scan-millis:60000}")
    public void deleteExpiredUnusedUploads() {
        images.deleteExpiredUnused();
    }
}
