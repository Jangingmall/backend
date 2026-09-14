package com.jangingmall.backend.chatbot.presentation;

import com.jangingmall.backend.chatbot.application.ChatCommand;
import com.jangingmall.backend.chatbot.application.ChatResponse;
import com.jangingmall.backend.chatbot.application.ChatService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chatbot/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ChatResponse.SessionView>> createSession(
        @AuthenticationPrincipal Long memberId
    ) {
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
    public ApiResponse<Void> endSession(
        @AuthenticationPrincipal Long memberId,
        @PathVariable UUID sessionId
    ) {
        chatService.endSession(new ChatCommand.EndSession(sessionId, memberId));
        return ApiResponse.noContent();
    }
}
