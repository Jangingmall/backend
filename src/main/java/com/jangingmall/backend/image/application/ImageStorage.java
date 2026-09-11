package com.jangingmall.backend.image.application;

import java.time.Duration;

public interface ImageStorage {
    String presignPut(String objectKey, String contentType, Duration validFor);
    boolean exists(String objectKey);
    void delete(String objectKey);
}
