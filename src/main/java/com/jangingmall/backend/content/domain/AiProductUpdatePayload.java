package com.jangingmall.backend.content.domain;

import java.util.List;

public record AiProductUpdatePayload(
    ProductPatch product
) {
    public record ProductPatch(
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
