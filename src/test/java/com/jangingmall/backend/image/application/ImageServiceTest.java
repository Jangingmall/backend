package com.jangingmall.backend.image.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.image.domain.ImageUpload;
import com.jangingmall.backend.image.domain.ImageUploadRepository;
import com.jangingmall.backend.member.application.MemberAccess;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock private MemberAccess memberAccess;
    @Mock private ImageUploadRepository uploads;
    @Mock private ImageStorage storage;
    @Mock private ObjectMapper objectMapper;
    private ImageService service;

    @BeforeEach
    void setUp() throws Exception {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setKeyPrefix("images");
        properties.setPresignExpirySeconds(300);
        service = new ImageService(memberAccess, uploads, storage, properties, new UlidGenerator(), objectMapper);
    }

    @Test
    @DisplayName("IMG-P0-001 WebP 3종 variant 요청은 각 S3 객체 키의 Presigned PUT URL을 함께 발급한다")
    void createsPresignedUploadForAllVariants() {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"320w\":\"images/product/1/a/320w.webp\"}");
        when(storage.presignPut(any(), eq("image/webp"), any(Duration.class))).thenAnswer(invocation ->
            "https://s3.example/" + invocation.getArgument(0));
        when(uploads.save(any(ImageUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImageService.PresignedUpload result = service.createPresignedUpload(1L,
            new ImageService.CreatePresignedUpload("bowl.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                List.of("320w", "640w", "1280w")));

        assertThat(result.imageId()).hasSize(26);
        assertThat(result.uploads()).extracting(ImageService.VariantUpload::variant)
            .containsExactly("320w", "640w", "1280w");
        assertThat(result.uploads()).allSatisfy(upload -> {
            assertThat(upload.objectKey()).startsWith("images/product/1/" + result.imageId() + "/");
            assertThat(upload.presignedUrl()).contains(upload.objectKey());
        });
        verify(uploads).save(any(ImageUpload.class));
    }

    @Test
    @DisplayName("IMG-P0-002 WebP가 아닌 업로드 요청은 S3 URL을 발급하지 않는다")
    void rejectsNonWebpUpload() {
        assertThatThrownBy(() -> service.createPresignedUpload(1L,
            new ImageService.CreatePresignedUpload("bowl.jpg", "image/jpeg", ImagePurpose.PRODUCT, 1200, 800,
                List.of("320w"))))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(storage, never()).presignPut(any(), any(), any());
    }

    @Test
    @DisplayName("IMG-P0-003 3종 variant가 빠진 요청은 S3 URL을 발급하지 않는다")
    void rejectsIncompleteVariants() {
        assertThatThrownBy(() -> service.createPresignedUpload(1L,
            new ImageService.CreatePresignedUpload("bowl.webp", "image/webp", ImagePurpose.PRODUCT, 1200, 800,
                List.of("320w", "640w"))))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(storage, never()).presignPut(any(), any(), any());
    }

    @Test
    @DisplayName("IMG-P0-004 반품 이미지는 소유권·용도·실제 업로드를 확인한 뒤 원자적으로 소비한다")
    void consumesOwnedUploadedReturnImage() throws Exception {
        ImageUpload upload = new ImageUpload("01JRETURNIMAGE000000000000", 1L, ImagePurpose.RETURN,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectMapper.readValue("{}", Map.class)).thenReturn(Map.of(
            "320w", Map.of("objectKey", "images/return/1/image/320w.webp"),
            "640w", Map.of("objectKey", "images/return/1/image/640w.webp"),
            "1280w", Map.of("objectKey", "images/return/1/image/1280w.webp")));
        when(storage.exists(any())).thenReturn(true);
        when(uploads.consumeIfOwnedAndActive(eq(upload.getId()), eq(1L), any(Instant.class))).thenReturn(1);

        assertThat(service.consumeOwned(1L, ImagePurpose.RETURN, List.of(upload.getId())))
            .containsExactly(upload.getId());
    }

    @Test
    @DisplayName("IMG-P0-005 저장된 variant가 불완전하면 다른 도메인에 연결하지 않는다")
    void rejectsConsumptionOfIncompleteStoredVariants() throws Exception {
        ImageUpload upload = new ImageUpload("01JINCOMPLETE0000000000000", 1L, ImagePurpose.PRODUCT,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectMapper.readValue("{}", Map.class)).thenReturn(Map.of(
            "320w", Map.of("objectKey", "images/product/1/image/320w.webp")));

        assertThatThrownBy(() -> service.consumeOwned(1L, ImagePurpose.PRODUCT, List.of(upload.getId())))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(uploads, never()).consumeIfOwnedAndActive(any(), any(), any());
    }

    @Test
    @DisplayName("IMG-P0-006 원자적 소비에 실패하면 409 CONCURRENT_UPDATE다")
    void rejectsConcurrentConsumption() throws Exception {
        ImageUpload upload = new ImageUpload("01JCONCURRENT000000000000", 1L, ImagePurpose.PRODUCT,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectMapper.readValue("{}", Map.class)).thenReturn(completeVariants("product"));
        when(storage.exists(any())).thenReturn(true);
        when(uploads.consumeIfOwnedAndActive(eq(upload.getId()), eq(1L), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consumeOwned(1L, ImagePurpose.PRODUCT, List.of(upload.getId())))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_UPDATE));
    }

    @Test
    @DisplayName("IMG-P2-007 다른 회원의 미사용 이미지 삭제는 403 FORBIDDEN이다")
    void rejectsDeletingAnotherMembersImage() {
        ImageUpload upload = new ImageUpload("01JOTHEROWNER0000000000000", 2L, ImagePurpose.PRODUCT,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findByIdForUpdate(upload.getId())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.deleteUnused(1L, upload.getId()))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(storage, never()).delete(any());
    }

    @Test
    @DisplayName("IMG-P0-008 만료된 미사용 업로드는 한 배치에서 S3와 DB 모두 정리한다")
    void deletesExpiredUnusedUploads() throws Exception {
        ImageUpload upload = new ImageUpload("01JEXPIREDIMAGE00000000000", 1L, ImagePurpose.CONTENT,
            1200, 800, "{}", Instant.now().minusSeconds(1));
        when(uploads.findTop100ByConsumedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAsc(any(Instant.class)))
            .thenReturn(List.of(upload));
        when(objectMapper.readValue("{}", Map.class)).thenReturn(completeVariants("content"));

        assertThat(service.deleteExpiredUnused()).isOne();
        verify(storage).delete("images/content/1/image/320w.webp");
        verify(storage).delete("images/content/1/image/640w.webp");
        verify(storage).delete("images/content/1/image/1280w.webp");
        verify(uploads).delete(upload);
    }

    @Test
    @DisplayName("IMG-P0-013 만료된 이미지 검증은 존재하지 않는 결과를 반환하고 소비하지 않는다")
    void doesNotVerifyExpiredUpload() {
        ImageUpload upload = new ImageUpload("01JEXPIREDVERIFY0000000000", 1L, ImagePurpose.PRODUCT,
            1200, 800, "{}", Instant.now().minusSeconds(1));
        when(uploads.findById(upload.getId())).thenReturn(Optional.of(upload));

        assertThat(service.verifyAndConsume(1L, upload.getId()))
            .isEqualTo(new ImageService.Verification(false, false, List.of()));
        verify(uploads, never()).consumeIfOwnedAndActive(any(), any(), any());
    }

    @Test
    @DisplayName("IMG-P0-014 다른 소유자의 이미지 검증은 ownerMatched=false다")
    void reportsOwnerMismatch() {
        ImageUpload upload = new ImageUpload("01JOWNERVERIFY00000000000", 2L, ImagePurpose.PRODUCT,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findById(upload.getId())).thenReturn(Optional.of(upload));

        assertThat(service.verifyAndConsume(1L, upload.getId()))
            .isEqualTo(new ImageService.Verification(true, false, List.of()));
        verify(storage, never()).exists(any());
    }

    @Test
    @DisplayName("IMG-P0-015 S3 variant 하나가 없으면 이미지를 소비하지 않는다")
    void doesNotConsumeWhenStoredObjectIsMissing() throws Exception {
        ImageUpload upload = new ImageUpload("01JMISSINGOBJECT0000000000", 1L, ImagePurpose.PRODUCT,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findById(upload.getId())).thenReturn(Optional.of(upload));
        when(objectMapper.readValue("{}", Map.class)).thenReturn(completeVariants("product"));
        when(storage.exists(any())).thenReturn(true);
        when(storage.exists("images/product/1/image/640w.webp")).thenReturn(false);

        ImageService.Verification result = service.verifyAndConsume(1L, upload.getId());

        assertThat(result.exists()).isFalse();
        assertThat(result.ownerMatched()).isTrue();
        verify(uploads, never()).consumeIfOwnedAndActive(any(), any(), any());
    }

    @Test
    @DisplayName("IMG-P2-016 본인의 미사용 이미지는 잠근 뒤 모든 variant와 메타데이터를 삭제한다")
    void deletesOwnedUnusedImage() throws Exception {
        ImageUpload upload = new ImageUpload("01JDELETEIMAGE00000000000", 1L, ImagePurpose.ARTISAN,
            1200, 800, "{}", Instant.now().plusSeconds(300));
        when(uploads.findByIdForUpdate(upload.getId())).thenReturn(Optional.of(upload));
        when(objectMapper.readValue("{}", Map.class)).thenReturn(completeVariants("artisan"));

        service.deleteUnused(1L, upload.getId());

        verify(storage).delete("images/artisan/1/image/320w.webp");
        verify(storage).delete("images/artisan/1/image/640w.webp");
        verify(storage).delete("images/artisan/1/image/1280w.webp");
        verify(uploads).delete(upload);
    }

    @Test
    @DisplayName("IMG-P2-017 이미 소비된 이미지는 삭제할 수 없다")
    void rejectsDeletingConsumedImage() {
        ImageUpload upload = mock(ImageUpload.class);
        when(upload.getId()).thenReturn("01JCONSUMEDIMAGE000000000");
        when(upload.getMemberId()).thenReturn(1L);
        when(upload.isConsumed()).thenReturn(true);
        when(uploads.findByIdForUpdate(upload.getId())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.deleteUnused(1L, upload.getId()))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(storage, never()).delete(any());
    }

    @Test
    @DisplayName("IMG-P0-018 원본 해상도가 10000px를 초과하면 URL을 발급하지 않는다")
    void rejectsOversizedDimensions() {
        assertThatThrownBy(() -> service.createPresignedUpload(1L,
            new ImageService.CreatePresignedUpload("huge.webp", "image/webp", ImagePurpose.CONTENT, 10_001, 800,
                List.of("320w", "640w", "1280w"))))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(storage, never()).presignPut(any(), any(), any());
    }

    private Map<String, Object> completeVariants(String purpose) {
        return Map.of(
            "320w", Map.of("objectKey", "images/" + purpose + "/1/image/320w.webp"),
            "640w", Map.of("objectKey", "images/" + purpose + "/1/image/640w.webp"),
            "1280w", Map.of("objectKey", "images/" + purpose + "/1/image/1280w.webp"));
    }
}
