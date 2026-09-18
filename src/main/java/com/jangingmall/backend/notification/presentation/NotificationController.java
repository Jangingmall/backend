package com.jangingmall.backend.notification.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.notification.application.NotificationCreateRequest;
import com.jangingmall.backend.notification.application.NotificationResponse;
import com.jangingmall.backend.notification.application.NotificationService;
import com.jangingmall.backend.notification.application.NotificationSseService;
import com.jangingmall.backend.notification.application.UnreadCountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('USER')")
    public SseEmitter stream(
        @AuthenticationPrincipal Long memberId
    ) {
        return notificationSseService.subscribe(memberId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<NotificationResponse> create(
        @AuthenticationPrincipal Long memberId,
        @RequestBody @Valid NotificationCreateRequest request
    ) {
        return ApiResponse.created(notificationService.create(memberId, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<List<NotificationResponse>> findAll(
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(notificationService.findAll(memberId));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<UnreadCountResponse> countUnread(
        @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.ok(notificationService.countUnread(memberId));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> markAllAsRead(
        @AuthenticationPrincipal Long memberId
    ) {
        notificationService.markAllAsRead(memberId);
        return ApiResponse.noContent();
    }

    @GetMapping("/{notificationId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<NotificationResponse> findOne(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long notificationId
    ) {
        return ApiResponse.ok(notificationService.findOne(notificationId, memberId));
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
