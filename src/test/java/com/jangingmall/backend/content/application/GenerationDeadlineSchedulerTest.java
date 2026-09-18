package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationDeadlineSchedulerTest {

    @Mock
    private ContentGenerationRepository generationRepository;

    @Mock
    private AiContentClient aiContentClient;

    @Captor
    private ArgumentCaptor<ContentGeneration> savedCaptor;

    private GenerationDeadlineScheduler scheduler;
    private GenerationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GenerationProperties(1861, 60_000);
        scheduler = new GenerationDeadlineScheduler(generationRepository, properties, aiContentClient);
    }

    private ContentGeneration queuedGeneration(Long id, LocalDateTime requestedAt) {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        generation.markQueued("job-" + id, "req-" + id, id.toString(), "http://ai/status/" + id);
        ReflectionTestUtils.setField(generation, "id", id);
        ReflectionTestUtils.setField(generation, "requestedAt", requestedAt);
        return generation;
    }

    @Test
    @DisplayName("데드라인 초과된 QUEUED generation은 FAILED로 전환된다")
    void expireOverdue() {
        LocalDateTime overdueAt = LocalDateTime.now().minusSeconds(1900);
        ContentGeneration overdue = queuedGeneration(1L, overdueAt);
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class))
        ).thenReturn(List.of(overdue));
        when(generationRepository.save(any())).thenReturn(overdue);

        scheduler.expireOverdueGenerations();

        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(savedCaptor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("데드라인 미초과 QUEUED generation은 처리하지 않는다")
    void skipWithinDeadline() {
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class))
        ).thenReturn(List.of());

        scheduler.expireOverdueGenerations();

        verify(generationRepository, never()).save(any());
    }

    @Test
    @DisplayName("데드라인 초과된 QUEUED가 여러 건이면 모두 FAILED로 전환된다")
    void expireMultiple() {
        LocalDateTime overdueAt = LocalDateTime.now().minusSeconds(2000);
        ContentGeneration gen1 = queuedGeneration(1L, overdueAt);
        ContentGeneration gen2 = queuedGeneration(2L, overdueAt);
        ContentGeneration gen3 = queuedGeneration(3L, overdueAt);
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class))
        ).thenReturn(List.of(gen1, gen2, gen3));
        when(generationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.expireOverdueGenerations();

        verify(generationRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("스캔 결과 없으면 save를 호출하지 않고 정상 종료된다")
    void emptyBatch() {
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class))
        ).thenReturn(List.of());

        scheduler.expireOverdueGenerations();

        verify(generationRepository, never()).save(any());
    }

    @Test
    @DisplayName("QUEUED 기준으로만 조회한다 — PROCESSING, COMPLETED, FAILED는 스캔 대상 아님")
    void onlyQueued() {
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class))
        ).thenReturn(List.of());

        scheduler.expireOverdueGenerations();

        verify(generationRepository).findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class)
        );
        verify(generationRepository, never()).findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.PROCESSING), any(LocalDateTime.class)
        );
        verify(generationRepository, never()).findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.COMPLETED), any(LocalDateTime.class)
        );
        verify(generationRepository, never()).findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.FAILED), any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("deadline 계산은 현재 시각에서 deadlineSeconds를 뺀 시점을 기준으로 한다")
    void deadlineCutoffIsCorrect() {
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), cutoffCaptor.capture())
        ).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusSeconds(properties.deadlineSeconds()).minusSeconds(1);
        scheduler.expireOverdueGenerations();
        LocalDateTime after = LocalDateTime.now().minusSeconds(properties.deadlineSeconds()).plusSeconds(1);

        LocalDateTime captured = cutoffCaptor.getValue();
        assertThat(captured).isAfter(before).isBefore(after);
    }

    @Test
    @DisplayName("FAILED로 전환된 generation은 completedAt이 null이 아니다")
    void failedGenerationHasCompletedAt() {
        LocalDateTime overdueAt = LocalDateTime.now().minusSeconds(2000);
        ContentGeneration overdue = queuedGeneration(1L, overdueAt);
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), any(LocalDateTime.class))
        ).thenReturn(List.of(overdue));
        when(generationRepository.save(any())).thenReturn(overdue);

        scheduler.expireOverdueGenerations();

        assertThat(overdue.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(overdue.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("deadlineSeconds=1861 이면 requestedAt + 1861초 초과 항목만 만료된다")
    void deadlineIs1861Seconds() {
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(generationRepository.findAllByStatusAndRequestedAtBefore(
            eq(GenerationStatus.QUEUED), cutoffCaptor.capture())
        ).thenReturn(List.of());

        scheduler.expireOverdueGenerations();

        LocalDateTime captured = cutoffCaptor.getValue();
        LocalDateTime expected = LocalDateTime.now().minusSeconds(properties.deadlineSeconds());
        assertThat(captured).isAfterOrEqualTo(expected.minusSeconds(2))
            .isBeforeOrEqualTo(expected.plusSeconds(2));
    }

    @Test
    @DisplayName("AI 상태가 DRAFT_READY이면 generation 상태를 DRAFT_READY로 전이한다")
    void pollDraftReady() {
        ContentGeneration gen = queuedGeneration(1L, LocalDateTime.now().minusSeconds(60));
        when(generationRepository.findAllByStatus(GenerationStatus.QUEUED)).thenReturn(List.of(gen));
        when(aiContentClient.getJobStatus("job-1")).thenReturn("DRAFT_READY");
        when(generationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.pollQueuedGenerations();

        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.DRAFT_READY);
    }

    @Test
    @DisplayName("AI 상태가 FAILED이면 generation 상태를 FAILED로 전이한다")
    void pollFailed() {
        ContentGeneration gen = queuedGeneration(2L, LocalDateTime.now().minusSeconds(60));
        when(generationRepository.findAllByStatus(GenerationStatus.QUEUED)).thenReturn(List.of(gen));
        when(aiContentClient.getJobStatus("job-2")).thenReturn("FAILED");
        when(generationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.pollQueuedGenerations();

        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    @DisplayName("AI 상태 조회 실패 시 예외를 삼키고 다음 generation을 계속 처리한다")
    void pollExceptionIsSwallowed() {
        ContentGeneration gen1 = queuedGeneration(1L, LocalDateTime.now().minusSeconds(60));
        ContentGeneration gen2 = queuedGeneration(2L, LocalDateTime.now().minusSeconds(60));
        when(generationRepository.findAllByStatus(GenerationStatus.QUEUED)).thenReturn(List.of(gen1, gen2));
        when(aiContentClient.getJobStatus("job-1")).thenThrow(new RuntimeException("AI 연결 실패"));
        when(aiContentClient.getJobStatus("job-2")).thenReturn("DRAFT_READY");
        when(generationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.pollQueuedGenerations();

        verify(generationRepository, times(1)).save(any());
        assertThat(gen2.getStatus()).isEqualTo(GenerationStatus.DRAFT_READY);
    }

    @Test
    @DisplayName("QUEUED generation이 없으면 AI 상태 조회를 하지 않는다")
    void pollEmptyBatch() {
        when(generationRepository.findAllByStatus(GenerationStatus.QUEUED)).thenReturn(List.of());

        scheduler.pollQueuedGenerations();

        verify(aiContentClient, never()).getJobStatus(any());
        verify(generationRepository, never()).save(any());
    }
}
