package com.jangingmall.backend.content.application;

import com.jangingmall.backend.image.application.ImageService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 이미지 ID(예: ULID)로 온 참조를 1280w 공개 URL로 바꾼다. URL이 아닌 모든 참조를 시도하는 마지막 순서의 구현이다. */
@Component
@Order(100)
public class ImageIdReferenceProvider implements ImageReferenceProvider {

    static final String AI_IMAGE_VARIANT = "1280w";

    private final ImageService imageService;

    public ImageIdReferenceProvider(ImageService imageService) {
        this.imageService = imageService;
    }

    @Override
    public boolean supports(String reference) {
        return !reference.isBlank();
    }

    @Override
    public Optional<String> toUrl(String reference) {
        return imageService.variantUrl(reference.trim(), AI_IMAGE_VARIANT);
    }
}
