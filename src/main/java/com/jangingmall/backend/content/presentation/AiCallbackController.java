package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.BeToAiPersistAckResponse;
import com.jangingmall.backend.content.application.GenerationCommand;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.global.config.AiProperties;
import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/internal/ai/generations")
@RequiredArgsConstructor
public class AiCallbackController {

    private final GenerationService generationService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BeToAiPersistAckResponse>> complete(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestPart("metadata") String metadataJson,
        @RequestPart("detail_page_image") MultipartFile detailPageImage,
        HttpServletRequest rawRequest
    ) throws Exception {
        validateToken(authorization);

        JsonNode metadata = objectMapper.readTree(metadataJson);
        String generationId = metadata.path("generationId").asText();
        String productId = metadata.path("productId").asText();
        JsonNode reactDocument = metadata.path("detailPage").path("reactDocument");

        Map<String, MultipartFile> sectionFiles = extractPrefixedParts(rawRequest, "detail_page_section_");
        Map<String, MultipartFile> photoFiles = extractPrefixedParts(rawRequest, "product_photo_");

        GenerationCommand.Complete command = new GenerationCommand.Complete(
            generationId, idempotencyKey, reactDocument.toString()
        );
        BeToAiPersistAckResponse ack = generationService.complete(command, detailPageImage, sectionFiles, photoFiles, productId);
        return ResponseEntity.ok(ApiResponse.ok(ack));
    }

    private void validateToken(String authorization) {
        String expected = aiProperties.internalToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (authorization == null || !authorization.equals("Bearer " + expected)) {
            throw new ForbiddenException("AI_INTERNAL_TOKEN 인증 실패");
        }
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
