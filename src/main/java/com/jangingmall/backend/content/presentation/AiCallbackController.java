package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.BeToAiPersistAckResponse;
import com.jangingmall.backend.content.application.GenerationCommand;
import com.jangingmall.backend.content.application.GenerationResponse;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/generations")
@RequiredArgsConstructor
public class AiCallbackController {

    private final GenerationService generationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{generationId}/complete")
    public ApiResponse<GenerationResponse> complete(
        @PathVariable Long generationId,
        @Valid @RequestBody AiCallbackRequest.Complete request
    ) {
        GenerationCommand.Complete command = new GenerationCommand.Complete(
            generationId, generationId.toString(), request.reactDocument().toString()
        );
        return ApiResponse.ok(generationService.complete(command));
    }

    @PostMapping(value = "/complete/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BeToAiPersistAckResponse>> completeMultipart(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestPart("metadata") String metadataJson,
        @RequestPart("detail_page_image") MultipartFile detailPageImage,
        HttpServletRequest rawRequest
    ) throws Exception {
        JsonNode metadata = objectMapper.readTree(metadataJson);
        String generationId = metadata.path("generationId").asText();
        String productId = metadata.path("productId").asText();
        JsonNode reactDocument = metadata.path("detailPage").path("reactDocument");

        Map<String, MultipartFile> sectionFiles = extractPrefixedParts(rawRequest, "detail_page_section_");
        Map<String, MultipartFile> photoFiles = extractPrefixedParts(rawRequest, "product_photo_");

        GenerationCommand.Complete command = new GenerationCommand.Complete(
            Long.parseLong(generationId), idempotencyKey, reactDocument.toString()
        );
        BeToAiPersistAckResponse ack = generationService.completeWithImages(
            command, detailPageImage, sectionFiles, photoFiles, productId
        );
        return ResponseEntity.ok(ApiResponse.ok(ack));
    }

    private Map<String, MultipartFile> extractPrefixedParts(HttpServletRequest request, String prefix) {
        if (!(request instanceof MultipartHttpServletRequest multipart)) {
            return Map.of();
        }
        Map<String, MultipartFile> result = new LinkedHashMap<>();
        multipart.getFileMap().forEach((name, file) -> {
            if (name.startsWith(prefix)) {
                result.put(name, file);
            }
        });
        return result;
    }
}
