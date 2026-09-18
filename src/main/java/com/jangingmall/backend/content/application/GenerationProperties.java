package com.jangingmall.backend.content.application;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "ai.generation")
@Validated
@Getter
@Setter
public class GenerationProperties {

    @Min(60)
    private long deadlineSeconds = 1_861;

    @Min(10_000)
    private long scanMillis = 60_000;
}
