package com.jangingmall.backend.chatbot.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public sealed interface ChatRequest {

    record SendMessage(
        @NotBlank @Size(max = 2000) String content
    ) implements ChatRequest {}
}
