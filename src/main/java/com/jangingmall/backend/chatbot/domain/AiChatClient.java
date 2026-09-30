package com.jangingmall.backend.chatbot.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AiChatClient {

    /** 백엔드 자체 폴백 문구. */
    String BACKEND_FALLBACK_REPLY = "현재 AI 추천을 이용할 수 없습니다";

    /** 챗봇 서버가 LLM·DB 장애 때 200으로 돌려주는 고정 안내 문구(GenAI chat_bot app/main.py). */
    String AI_SERVER_FALLBACK_REPLY = "지금 답변이 지연되고 있어요. 잠시 후 다시 시도해 주세요.";

    /**
     * 장애 안내 문구 모음. 대화 이력에는 저장되지만 다음 턴의 AI 입력에는 넣지 않는다 — 넣으면 챗봇이 그 안내를
     * 자기 발화로 읽어 이후 답변이 흐트러진다.
     */
    Set<String> FALLBACK_REPLIES = Set.of(BACKEND_FALLBACK_REPLY, AI_SERVER_FALLBACK_REPLY);

    AiChatResult chat(UUID sessionId, String message, List<ChatMessage> history);

    record ProductCard(Long productId, String reason) {}

    record AiChatResult(
        String reply,
        String intent,
        List<ProductCard> products,
        List<String> suggestions
    ) {}
}
