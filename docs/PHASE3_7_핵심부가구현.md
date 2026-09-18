# 산출물 7 — 핵심 부가 구현

---

## 실시간 알림 (SSE)

### NotificationController.java
`src/main/java/com/jangingmall/backend/notification/presentation/NotificationController.java`

```java
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('USER')")
    public SseEmitter stream(@AuthenticationPrincipal Long memberId) {
        return notificationSseService.subscribe(memberId);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<List<NotificationResponse>> findAll(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(notificationService.findAll(memberId));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<UnreadCountResponse> countUnread(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.ok(notificationService.countUnread(memberId));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> markAsRead(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long notificationId
    ) {
        notificationService.markAsRead(notificationId, memberId);
        return ApiResponse.noContent();
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal Long memberId) {
        notificationService.markAllAsRead(memberId);
        return ApiResponse.noContent();
    }

    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long notificationId
    ) {
        notificationService.delete(notificationId, memberId);
        return ApiResponse.noContent();
    }
}
```

---

## AI 콘텐츠 생성 파이프라인

### GenerationController.java
`src/main/java/com/jangingmall/backend/content/presentation/GenerationController.java`

```java
@RestController
@RequestMapping("/api/content/products/{productId}/generations")
@RequiredArgsConstructor
public class GenerationController {

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<GenerationResponse>> request(
        @PathVariable Long productId,
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody GenerationRequest.Create request
    ) {
        GenerationCommand.Request command = new GenerationCommand.Request(
            productId, memberId, request.images(), request.productName(), request.howMade(), request.careTips()
        );
        return ResponseEntity.status(202).body(ApiResponse.accepted(generationService.request(command)));
    }

    @GetMapping("/{generationId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<GenerationResponse> poll(
        @PathVariable Long productId,
        @PathVariable Long generationId,
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(generationService.poll(productId, generationId, memberId));
    }
}
```

### AiCallbackController.java (AI → BE 비동기 콜백)
`src/main/java/com/jangingmall/backend/content/presentation/AiCallbackController.java`

```java
@RestController
@RequestMapping("/internal/generations")
@RequiredArgsConstructor
public class AiCallbackController {

    @PostMapping(value = "/{generationId}/completion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BeToAiPersistAckResponse>> completeMultipart(
        @PathVariable Long generationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestPart("metadata") String metadataJson,
        @RequestPart(value = "detail_page_image", required = false) MultipartFile detailPageImage,
        HttpServletRequest rawRequest
    ) throws Exception {
        JsonNode metadata = objectMapper.readTree(metadataJson);
        String productId = metadata.path("productId").asText();
        JsonNode reactDocument = metadata.path("detailPage").path("reactDocument");

        Map<String, MultipartFile> sectionFiles = extractPrefixedParts(rawRequest, "detail_page_section_");
        Map<String, MultipartFile> photoFiles = extractPrefixedParts(rawRequest, "product_photo_");

        GenerationCommand.Complete command = new GenerationCommand.Complete(
            generationId, idempotencyKey, reactDocument.toString()
        );
        BeToAiPersistAckResponse ack = generationService.completeWithImages(
            command, detailPageImage, sectionFiles, photoFiles, productId
        );
        return ResponseEntity.ok(ApiResponse.ok(ack));
    }
}
```

---

## AI 챗봇

### ChatController.java
`src/main/java/com/jangingmall/backend/chatbot/presentation/ChatController.java`

```java
@RestController
@RequestMapping("/api/chatbot/sessions")
@RequiredArgsConstructor
public class ChatController {

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ChatResponse.SessionView>> createSession(@AuthenticationPrincipal Long memberId) {
        ChatResponse.SessionView response = chatService.createSession(new ChatCommand.CreateSession(memberId));
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @PostMapping("/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ChatResponse.SendResult>> sendMessage(
        @AuthenticationPrincipal Long memberId,
        @PathVariable UUID sessionId,
        @Valid @RequestBody ChatRequest.SendMessage request
    ) {
        ChatResponse.SendResult response = chatService.sendMessage(
            new ChatCommand.SendMessage(sessionId, memberId, request.content())
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<List<ChatResponse.MessageView>> messages(
        @AuthenticationPrincipal Long memberId,
        @PathVariable UUID sessionId
    ) {
        return ApiResponse.ok(chatService.findMessages(sessionId, memberId));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> endSession(@AuthenticationPrincipal Long memberId, @PathVariable UUID sessionId) {
        chatService.endSession(new ChatCommand.EndSession(sessionId, memberId));
        return ApiResponse.noContent();
    }
}
```

---

## 이미지 업로드 (S3 Presigned URL)

### ImageController.java
`src/main/java/com/jangingmall/backend/image/presentation/ImageController.java`

```java
@RestController
@RequiredArgsConstructor
public class ImageController {

    @PostMapping("/api/images/presigned-url")
    public ApiResponse<ImageService.PresignedUpload> createPresignedUrl(
        @AuthenticationPrincipal Long memberId,
        Authentication authentication,
        @Valid @RequestBody PresignedUrlRequest request
    ) {
        Long resolvedMemberId = isAgent(authentication) ? request.resolveAgentMemberId() : memberId;
        return ApiResponse.ok(images.createPresignedUpload(resolvedMemberId, request.toCommand()));
    }

    private boolean isAgent(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }

    @DeleteMapping("/api/images/{imageId}")
    public ApiResponse<Void> deleteUnused(@AuthenticationPrincipal Long memberId, @PathVariable String imageId) {
        images.deleteUnused(memberId, imageId);
        return ApiResponse.ok(null);
    }

    public record PresignedUrlRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @NotNull ImagePurpose purpose,
        @Positive int sourceWidth,
        @Positive int sourceHeight,
        @NotEmpty @Size(max = 3) List<@Valid @NotNull VariantRequest> variants,
        Long memberId
    ) {}
}
```
