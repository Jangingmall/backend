package com.jangingmall.backend.content.domain;

import java.util.List;

public record AiProductSyncPayload(
    ArtisanInfo artisan,
    ProductInfo product
) {
    public record ArtisanInfo(
        Long artisanId,
        String name,
        String certificationLevel,
        String introduction
    ) {}

    public record ProductInfo(
        Long productId,
        String title,
        String category,
        String material,
        int price,
        List<String> giftTheme,
        List<String> purposeTags,
        String makingStory,
        String usageCare,
        Integer productionPeriodDays,
        List<String> color
    ) {}
}
