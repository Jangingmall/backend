package com.jangingmall.backend.chatbot.application;

import com.jangingmall.backend.chatbot.domain.AiChatClient;
import com.jangingmall.backend.chatbot.domain.AiChatClient.AiChatResult;
import com.jangingmall.backend.chatbot.domain.ChatErrorMessage;
import com.jangingmall.backend.chatbot.domain.ChatMessage;
import com.jangingmall.backend.chatbot.domain.ChatMessageRepository;
import com.jangingmall.backend.chatbot.domain.ChatSender;
import com.jangingmall.backend.chatbot.domain.ChatSession;
import com.jangingmall.backend.chatbot.domain.ChatSessionRepository;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AiChatClient aiChatClient;

    @Transactional
    public ChatResponse.SessionView createSession(ChatCommand.CreateSession command) {
        return ChatResponse.SessionView.from(sessionRepository.save(ChatSession.create(command.memberId())));
    }

    @Transactional
    public ChatResponse.SendResult sendMessage(ChatCommand.SendMessage command) {
        ChatSession session = getSession(command.sessionId());
        verifyOwner(session, command.memberId());
        if (session.isEnded()) {
            throw new BusinessRuleViolationException(ChatErrorMessage.SESSION_ALREADY_ENDED.message());
        }

        ChatMessage userMessage = messageRepository.save(
            ChatMessage.of(command.sessionId(), ChatSender.USER, command.content())
        );

        List<ChatMessage> history = messageRepository.findBySessionId(command.sessionId());
        AiChatResult result = aiChatClient.chat(command.sessionId(), command.content(), history);

        ChatMessage botMessage = messageRepository.save(
            ChatMessage.of(command.sessionId(), ChatSender.ADMIN, result.reply())
        );

        return new ChatResponse.SendResult(
            ChatResponse.MessageView.from(userMessage),
            ChatResponse.MessageView.from(botMessage),
            result.productIds(),
            result.suggestions()
        );
    }

    @Transactional(readOnly = true)
    public List<ChatResponse.MessageView> findMessages(UUID sessionId, Long memberId) {
        ChatSession session = getSession(sessionId);
        verifyOwner(session, memberId);
        return messageRepository.findBySessionId(sessionId)
            .stream()
            .map(ChatResponse.MessageView::from)
            .toList();
    }

    @Transactional
    public void endSession(ChatCommand.EndSession command) {
        ChatSession session = getSession(command.sessionId());
        verifyOwner(session, command.memberId());
        if (session.isEnded()) {
            throw new BusinessRuleViolationException(ChatErrorMessage.SESSION_ALREADY_ENDED.message());
        }
        session.end();
    }

    private ChatSession getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException(ChatErrorMessage.SESSION_NOT_FOUND.message()));
    }

    private void verifyOwner(ChatSession session, Long memberId) {
        if (!session.getMemberId().equals(memberId)) {
            throw new ForbiddenException(ChatErrorMessage.SESSION_FORBIDDEN.message());
        }
    }
}
