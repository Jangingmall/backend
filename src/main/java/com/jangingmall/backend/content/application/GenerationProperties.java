package com.jangingmall.backend.content.application;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai.generation")
@Validated
public record GenerationProperties(
    @Min(60) @DefaultValue("1861") long deadlineSeconds,
    @Min(10_000) @DefaultValue("60000") long scanMillis
) {}
