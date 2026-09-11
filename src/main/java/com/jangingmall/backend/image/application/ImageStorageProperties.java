package com.jangingmall.backend.image.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "image.storage")
@Getter
@Setter
public class ImageStorageProperties {
    private String bucket = "";
    private String region = "ap-northeast-2";
    private String keyPrefix = "images";
    private int presignExpirySeconds = 300;
}
