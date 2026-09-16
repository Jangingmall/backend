package com.jangingmall.backend.content.domain;

import java.util.List;

public record AiProductSyncPayload(
    ArtisanInfo artisan,
    ProductInfo product
) {
    public record ArtisanInfo(
        Long artisan_id,
        String business_name,
        String certification_level,
        String region
    ) {}

    public record ProductInfo(
        Long product_id,
        String name,
        String category_code,
        String subcategory_code,
        String material,
        int price,
        String color,
        List<String> gift_theme,
        List<String> purpose_tags,
        String making_story,
        String usage_care,
        Integer production_period_days,
        String status
    ) {}
}
