package com.jangingmall.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
    String chatBotUrl,
    String contentUrl,
    String internalToken,
    int timeoutSeconds
) {}
