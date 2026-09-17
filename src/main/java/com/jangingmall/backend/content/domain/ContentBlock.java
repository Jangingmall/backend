package com.jangingmall.backend.content.domain;

public record ContentBlock(
    int order,
    BlockTag tag,
    String text,
    String imageUrl
) {
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }
}
