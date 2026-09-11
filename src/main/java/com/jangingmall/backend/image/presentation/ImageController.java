package com.jangingmall.backend.image.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
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
                                      @NotEmpty @Size(max = 3) List<@NotBlank String> variants) {
        ImageService.CreatePresignedUpload toCommand() {
            return new ImageService.CreatePresignedUpload(fileName, contentType, purpose, sourceWidth, sourceHeight, variants);
        }
    }

    public record VerifyImageRequest(@NotBlank @Size(max = 30) String imageId, @NotNull Long requesterId) {}
}
