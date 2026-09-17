package com.jangingmall.backend.image.infrastructure;

import com.jangingmall.backend.image.application.ImageStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Configuration
public class ImageStorageConfig {

    private static final Duration S3_TIMEOUT = Duration.ofSeconds(30);

    @Bean(destroyMethod = "close")
    S3Presigner imageS3Presigner(ImageStorageProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.getRegion())).build();
    }

    @Bean(destroyMethod = "close")
    S3Client imageS3Client(ImageStorageProperties properties) {
        return S3Client.builder()
            .region(Region.of(properties.getRegion()))
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallTimeout(S3_TIMEOUT)
                .apiCallAttemptTimeout(S3_TIMEOUT)
                .build())
            .build();
    }
}
