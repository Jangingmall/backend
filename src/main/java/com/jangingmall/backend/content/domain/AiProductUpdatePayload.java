package com.jangingmall.backend.content.domain;

import java.util.List;

public record AiProductUpdatePayload(
    ProductPatch product
) {
    public record ProductPatch(
        String name,
        String category_code,
        String material,
        int price,
        String color,
        List<String> gift_theme,
        List<String> purpose_tags,
        String making_story,
        String usage_care,
        Integer production_period_days
    ) {}
}
