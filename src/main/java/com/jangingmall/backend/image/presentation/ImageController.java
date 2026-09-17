package com.jangingmall.backend.image.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImageController {

    private final ImageService images;

    @PostMapping("/api/images/presigned-url")
    public ApiResponse<ImageService.PresignedUpload> createPresignedUrl(@AuthenticationPrincipal Long memberId,
                                                                         @Valid @RequestBody PresignedUrlRequest request) {
        return ApiResponse.ok(images.createPresignedUpload(memberId, request.toCommand()));
    }

    @DeleteMapping("/api/images/{imageId}")
    public ApiResponse<Void> deleteUnused(@AuthenticationPrincipal Long memberId, @PathVariable String imageId) {
        images.deleteUnused(memberId, imageId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/internal/images/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ImageService.Verification> verify(@Valid @RequestBody VerifyImageRequest request) {
        return ApiResponse.ok(images.verifyAndConsume(request.requesterId(), request.imageId()));
    }

    public record PresignedUrlRequest(@NotBlank @Size(max = 255) String fileName,
                                      @NotBlank @Size(max = 100) String contentType,
                                      @NotNull ImagePurpose purpose,
                                      @Positive int sourceWidth,
                                      @Positive int sourceHeight,
                                      @NotEmpty @Size(max = 3) List<@Valid @NotNull VariantRequest> variants) {
        ImageService.CreatePresignedUpload toCommand() {
            return new ImageService.CreatePresignedUpload(fileName, contentType, purpose, sourceWidth, sourceHeight,
                variants.stream().map(VariantRequest::toCommand).toList());
        }
    }

    /**
     * The collaboration contract sends variant names (for example, "320w").
     * sizeBytes remains accepted for clients that want Content-Length signing,
     * but is optional so the contract can be used without leaking transformed
     * file sizes into the request.
     */
    public record VariantRequest(@NotBlank String name, @Positive Long sizeBytes) {
        public VariantRequest(String name, long sizeBytes) {
            this(name, Long.valueOf(sizeBytes));
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public VariantRequest(String name) {
            this(name, null);
        }

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public VariantRequest(@JsonProperty("name") String name,
                              @JsonProperty("sizeBytes") Long sizeBytes) {
            this.name = name;
            this.sizeBytes = sizeBytes;
        }

        /** Serialize the canonical collaboration shape: ["320w", "640w", "1280w"]. */
        @JsonValue
        public String jsonValue() {
            return name;
        }

        ImageService.UploadVariant toCommand() {
            return new ImageService.UploadVariant(name, sizeBytes == null ? 0L : sizeBytes);
        }
    }

    public record VerifyImageRequest(@NotBlank @Size(max = 30) String imageId, @NotNull Long requesterId) {}
}
