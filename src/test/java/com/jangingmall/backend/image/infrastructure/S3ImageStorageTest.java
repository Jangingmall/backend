package com.jangingmall.backend.image.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.image.application.ImageStorageProperties;
import com.jangingmall.backend.image.domain.ImagePurpose;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

    @Mock private S3Presigner presigner;
    @Mock private S3Client s3;
    @Mock private PresignedPutObjectRequest signedRequest;
    private S3ImageStorage storage;

    @BeforeEach
    void setUp() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setBucket("public-image-bucket");
        properties.setReturnBucket("private-return-bucket");
        storage = new S3ImageStorage(presigner, s3, properties);
    }

    @Test
    @DisplayName("IMG-P0-021 공개 이미지와 반품 이미지는 서로 다른 S3 버킷으로 서명한다")
    void selectsBucketByPurpose() throws Exception {
        when(signedRequest.url()).thenReturn(URI.create("https://s3.example/upload").toURL());
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signedRequest);
        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        storage.presignPut(ImagePurpose.PRODUCT, "images/product/id/1280w.webp", "image/webp", 4096L,
            Duration.ofMinutes(5));
        storage.presignPut(ImagePurpose.RETURN, "images/return/id/1280w.webp", "image/webp", 2048L,
            Duration.ofMinutes(5));

        org.mockito.Mockito.verify(presigner, org.mockito.Mockito.times(2)).presignPutObject(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(request -> request.putObjectRequest().bucket())
            .containsExactly("public-image-bucket", "private-return-bucket");
        assertThat(captor.getAllValues())
            .extracting(request -> request.putObjectRequest().contentLength())
            .containsExactly(4096L, 2048L);
    }

    @Test
    @DisplayName("IMG-P0-023 S3 객체의 Content-Type과 크기가 정책 범위일 때만 유효하다")
    void validatesUploadedObjectMetadata() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder().contentType("image/webp").contentLength(1024L).build(),
            HeadObjectResponse.builder().contentType("image/webp").contentLength(10L * 1024 * 1024 + 1).build(),
            HeadObjectResponse.builder().contentType("image/png").contentLength(1024L).build());

        assertThat(storage.isValid(ImagePurpose.PRODUCT, "images/product/id/1280w.webp", "image/webp",
            10L * 1024 * 1024)).isTrue();
        assertThat(storage.isValid(ImagePurpose.PRODUCT, "images/product/id/1280w.webp", "image/webp",
            10L * 1024 * 1024)).isFalse();
        assertThat(storage.isValid(ImagePurpose.PRODUCT, "images/product/id/1280w.webp", "image/webp",
            10L * 1024 * 1024)).isFalse();
    }
}
