package com.jangingmall.backend.notification.application;

import com.jangingmall.backend.notification.domain.NotificationStatus;
import com.jangingmall.backend.notification.infrastructure.NotificationSseEmitterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSseServiceTest {

    @Mock
    private NotificationSseEmitterRepository emitterRepository;

    private NotificationSseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new NotificationSseService(emitterRepository);
    }

    @Test
    @DisplayName("subscribe 호출 시 emitter가 저장소에 등록된다")
    void subscribe_registersEmitter() {
        when(emitterRepository.add(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        SseEmitter emitter = sseService.subscribe(1L);

        assertThat(emitter).isNotNull();
        verify(emitterRepository).add(eq(1L), any(SseEmitter.class));
    }

    @Test
    @DisplayName("subscribe 시 반환된 emitter는 null이 아니고 저장소에 저장된다")
    void subscribe_storesEmitterInRepository() {
        when(emitterRepository.add(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        SseEmitter emitter = sseService.subscribe(1L);

        assertThat(emitter).isNotNull();
        verify(emitterRepository).add(eq(1L), eq(emitter));
    }

    @Test
    @DisplayName("onNotificationCreated 호출 시 구독 중인 emitter로 이벤트가 전송된다")
    void onNotificationCreated_sendsToSubscribedEmitter() {
        SseEmitter emitter = new SseEmitter(1000L);
        when(emitterRepository.findAll(1L)).thenReturn(List.of(emitter));

        NotificationResponse notification = new NotificationResponse(
            1L, "주문 완료", "청자 상감 다완 주문이 접수되었습니다.",
            NotificationStatus.UNREAD, LocalDateTime.now()
        );

        sseService.onNotificationCreated(new NotificationCreatedEvent(1L, notification));

        verify(emitterRepository).findAll(1L);
    }

    @Test
    @DisplayName("이미 완료된 emitter에 전송 실패 시 저장소에서 제거된다")
    void onNotificationCreated_removesDeadEmitter() {
        SseEmitter deadEmitter = new SseEmitter(0L);
        deadEmitter.complete();

        List<SseEmitter> mutableList = new ArrayList<>(List.of(deadEmitter));
        when(emitterRepository.findAll(1L)).thenReturn(mutableList);

        NotificationResponse notification = new NotificationResponse(
            1L, "제목", "내용", NotificationStatus.UNREAD, LocalDateTime.now()
        );

        sseService.onNotificationCreated(new NotificationCreatedEvent(1L, notification));

        verify(emitterRepository).remove(eq(1L), eq(deadEmitter));
    }
}
