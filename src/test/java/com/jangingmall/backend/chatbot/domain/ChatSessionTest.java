package com.jangingmall.backend.chatbot.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionTest {

    @Test
    @DisplayName("세션 생성 시 종료 상태가 아니다")
    void create_notEnded() {
        ChatSession session = ChatSession.create(1L);

        assertThat(session.isEnded()).isFalse();
        assertThat(session.getEndedAt()).isNull();
    }

    @Test
    @DisplayName("세션 종료 시 endedAt이 설정된다")
    void end_setsEndedAt() {
        ChatSession session = ChatSession.create(1L);

        session.end();

        assertThat(session.isEnded()).isTrue();
        assertThat(session.getEndedAt()).isNotNull();
    }
}
