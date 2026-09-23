package com.jangingmall.backend.revalidate.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "revalidate")
public record RevalidateProperties(
    String webhookUrl,
    String webhookSecret,
    int timeoutSeconds
) {}
