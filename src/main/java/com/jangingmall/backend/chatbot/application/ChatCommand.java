package com.jangingmall.backend.chatbot.application;

import java.util.UUID;

public sealed interface ChatCommand {

    record CreateSession(Long memberId) implements ChatCommand {}

    record SendMessage(UUID sessionId, Long memberId, String content) implements ChatCommand {}

    record EndSession(UUID sessionId, Long memberId) implements ChatCommand {}
}
