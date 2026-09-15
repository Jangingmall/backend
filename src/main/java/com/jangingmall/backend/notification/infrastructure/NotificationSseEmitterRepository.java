package com.jangingmall.backend.notification.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class NotificationSseEmitterRepository {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

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
