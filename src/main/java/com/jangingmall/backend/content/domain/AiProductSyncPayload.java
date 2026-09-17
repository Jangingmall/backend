package com.jangingmall.backend.content.domain;

import java.util.List;

public record AiProductSyncPayload(
    ArtisanInfo artisan,
    ProductInfo product
) {
    public record ArtisanInfo(
        Long artisan_id,
        String name,
        String certification_level,
        String introduction
    ) {}

    public record ProductInfo(
        Long product_id,
        String title,
        String category,
        String material,
        int price,
        List<String> gift_theme,
        List<String> purpose_tags,
        String making_story,
        String usage_care,
        Integer production_period_days,
        List<String> color
    ) {}
}
