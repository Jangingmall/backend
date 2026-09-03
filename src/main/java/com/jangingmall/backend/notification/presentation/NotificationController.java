package com.jangingmall.backend.notification.presentation;

import com.jangingmall.backend.notification.application.NotificationResponse;
import com.jangingmall.backend.notification.application.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // depth-1: Controller → depth-2: Service.findAll → (에러 없음)
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> findAll(@RequestParam Long memberId) {
        return ResponseEntity.ok(notificationService.findAll(memberId));
    }

    // depth-1: Controller → depth-2: Service.findOne
    //   → depth-3: findOrThrow → NotFoundException
    //   → depth-4: validateOwnership → ForbiddenException
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationResponse> findOne(
        @PathVariable Long notificationId,
        @RequestParam Long requesterId
    ) {
        return ResponseEntity.ok(notificationService.findOne(notificationId, requesterId));
    }

    // depth-1: Controller → depth-2: Service.markAsRead
    //   → depth-3: findOrThrow → NotFoundException
    //   → depth-4: validateOwnership → ForbiddenException
    //   → depth-4: markAsRead() → BusinessRuleViolationException
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
        @PathVariable Long notificationId,
        @RequestParam Long requesterId
    ) {
        notificationService.markAsRead(notificationId, requesterId);
        return ResponseEntity.noContent().build();
    }

    // depth-1: Controller → depth-2: Service.delete
    //   → depth-3: findOrThrow → NotFoundException
    //   → depth-4: validateOwnership → ForbiddenException
    //   → depth-4: delete() → BusinessRuleViolationException
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(
        @PathVariable Long notificationId,
        @RequestParam Long requesterId
    ) {
        notificationService.delete(notificationId, requesterId);
        return ResponseEntity.noContent().build();
    }
}
