package com.jangingmall.backend.image.infrastructure;

import com.jangingmall.backend.image.application.ImageStorage;
import com.jangingmall.backend.image.application.ImageStorageProperties;
import com.jangingmall.backend.image.domain.ImagePurpose;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
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
    public String presignPut(ImagePurpose purpose, String objectKey, String contentType, long contentLength,
                             Duration validFor) {
        PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket(purpose)).key(objectKey)
            .contentType(contentType);
        if (contentLength > 0) {
            put.contentLength(contentLength);
        }
        PresignedPutObjectRequest request = presigner.presignPutObject(PutObjectPresignRequest.builder()
            .signatureDuration(validFor)
            .putObjectRequest(put.build())
            .build());
        return request.url().toString();
    }

    @Override
    public boolean isValid(ImagePurpose purpose, String objectKey, String contentType, long maxContentLength) {
        try {
            var object = s3.headObject(HeadObjectRequest.builder().bucket(bucket(purpose)).key(objectKey).build());
            return object.contentLength() != null && object.contentLength() > 0
                && object.contentLength() <= maxContentLength
                && contentType.equalsIgnoreCase(object.contentType());
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new IllegalStateException("S3 이미지 존재 여부를 확인할 수 없습니다.", exception);
        }
    }

    @Override
    public void delete(ImagePurpose purpose, String objectKey) {
        try {
            s3.deleteObject(request -> request.bucket(bucket(purpose)).key(objectKey));
        } catch (S3Exception exception) {
            throw new IllegalStateException("S3 이미지를 삭제할 수 없습니다.", exception);
        }
    }

    @Override
    public void put(ImagePurpose purpose, String objectKey, String contentType, byte[] data) {
        try {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket(purpose))
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build(),
                RequestBody.fromBytes(data)
            );
        } catch (S3Exception exception) {
            throw new IllegalStateException("S3 이미지 업로드에 실패했습니다.", exception);
        }
    }

    private String bucket(ImagePurpose purpose) {
        String bucket = purpose == ImagePurpose.RETURN ? properties.getReturnBucket() : properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            String type = purpose == ImagePurpose.RETURN ? "반품 이미지" : "공개 이미지";
            throw new IllegalStateException(type + " S3 버킷이 설정되지 않았습니다.");
        }
        return bucket;
    }
}
