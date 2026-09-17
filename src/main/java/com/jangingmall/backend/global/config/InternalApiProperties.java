package com.jangingmall.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal-api")
public record InternalApiProperties(
    String backendAuthToken
) {}
