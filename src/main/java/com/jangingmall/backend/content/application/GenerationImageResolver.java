package com.jangingmall.backend.content.application;

import com.jangingmall.backend.image.application.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * AI는 사진을 URL로 내려받는다. 요청에 이미지 ID가 담겨 오면 제출 직전에 1280w 공개 URL로 바꾼다.
 * URL은 그대로 두고, 찾을 수 없는 값은 원본을 유지해 이후 제출 단계에서 기존처럼 이미지 오류로 처리되게 한다.
 */
@Slf4j
@Component
public class GenerationImageResolver {

    static final String AI_IMAGE_VARIANT = "1280w";

    private final ImageService imageService;

    public GenerationImageResolver(ImageService imageService) {
        this.imageService = imageService;
    }

    public List<String> resolve(List<String> images) {
        if (images == null || images.isEmpty()) {
            return images;
        }
        return images.stream().map(this::resolveOne).toList();
    }

    private String resolveOne(String image) {
        if (image == null || isHttpUrl(image)) {
            return image;
        }
        String imageId = image.trim();
        return imageService.variantUrl(imageId, AI_IMAGE_VARIANT).orElseGet(() -> {
            log.warn("이미지 ID에 해당하는 {} URL을 찾지 못했습니다 imageId={}", AI_IMAGE_VARIANT, imageId);
            return image;
        });
    }

    private static boolean isHttpUrl(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
