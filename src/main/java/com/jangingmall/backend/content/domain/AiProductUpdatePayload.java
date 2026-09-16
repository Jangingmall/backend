package com.jangingmall.backend.content.domain;

import java.util.List;

public record AiProductUpdatePayload(
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
