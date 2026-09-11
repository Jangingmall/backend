package com.jangingmall.backend.image.infrastructure;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.image.application.ImageStorage;
import com.jangingmall.backend.image.application.ImageStorageProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Presigner presigner;
    private final S3Client s3;
    private final ImageStorageProperties properties;

    @Override
    public String presignPut(String objectKey, String contentType, Duration validFor) {
        PresignedPutObjectRequest request = presigner.presignPutObject(PutObjectPresignRequest.builder()
            .signatureDuration(validFor)
            .putObjectRequest(PutObjectRequest.builder().bucket(bucket()).key(objectKey).contentType(contentType).build())
            .build());
        return request.url().toString();
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket()).key(objectKey).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new BusinessRuleViolationException("S3 이미지 존재 여부를 확인할 수 없습니다.");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3.deleteObject(request -> request.bucket(bucket()).key(objectKey));
        } catch (S3Exception exception) {
            throw new BusinessRuleViolationException("S3 이미지를 삭제할 수 없습니다.");
        }
    }

    private String bucket() {
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new BusinessRuleViolationException("이미지 S3 버킷이 설정되지 않았습니다.");
        }
        return properties.getBucket();
    }
}
