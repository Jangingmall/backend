package com.jangingmall.backend.image.application;

import com.jangingmall.backend.image.domain.ImagePurpose;
import java.time.Duration;

public interface ImageStorage {
    String presignPut(ImagePurpose purpose, String objectKey, String contentType, long contentLength,
                      Duration validFor);
    boolean isValid(ImagePurpose purpose, String objectKey, String contentType, long maxContentLength);
    /** 객체가 있는지만 확인한다(형식·크기는 보지 않는다). */
    boolean exists(ImagePurpose purpose, String objectKey);
    void delete(ImagePurpose purpose, String objectKey);

    void put(ImagePurpose purpose, String objectKey, String contentType, byte[] data);
}
