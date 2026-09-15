package com.jangingmall.backend.notification.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificationSseEmitterRepository {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationSseEmitterRepository(MeterRegistry meterRegistry) {
        Gauge.builder("sse.active.connections", emitters, map ->
                map.values().stream().mapToInt(List::size).sum())
            .description("현재 활성 SSE 연결 수")
            .register(meterRegistry);
    }

    public SseEmitter add(Long memberId, SseEmitter emitter) {
        emitters.computeIfAbsent(memberId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        return emitter;
    }

    public List<SseEmitter> findAll(Long memberId) {
        return emitters.getOrDefault(memberId, new CopyOnWriteArrayList<>());
    }

    public void remove(Long memberId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(memberId, list);
            }
        }
    }

    public int countConnected() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }
}
