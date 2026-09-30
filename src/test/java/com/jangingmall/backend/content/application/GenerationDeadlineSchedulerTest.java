package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationDeadlineSchedulerTest {

    private static final long DEADLINE_SECONDS = 1861;

    @Mock
    private ContentGenerationRepository generationRepository;

    @Mock
    private AiContentClient aiContentClient;

    @Captor
    private ArgumentCaptor<ContentGeneration> savedCaptor;

    private GenerationDeadlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        GenerationProperties properties = new GenerationProperties(DEADLINE_SECONDS, 60_000);
        scheduler = new GenerationDeadlineScheduler(generationRepository, properties, aiContentClient);
    }

    private ContentGeneration queuedGeneration(Long id, LocalDateTime requestedAt) {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        generation.markQueued("job-" + id, "req-" + id, id.toString(), "http://ai/status/" + id);
        ReflectionTestUtils.setField(generation, "id", id);
        ReflectionTestUtils.setField(generation, "requestedAt", requestedAt);
        return generation;
    }

    private ContentGeneration withinDeadline(Long id) {
        return queuedGeneration(id, LocalDateTime.now().minusSeconds(60));
    }

    private ContentGeneration overdue(Long id) {
        return queuedGeneration(id, LocalDateTime.now().minusSeconds(DEADLINE_SECONDS + 30));
    }

    private void givenQueued(ContentGeneration... generations) {
        when(generationRepository.findAllByStatus(GenerationStatus.QUEUED)).thenReturn(List.of(generations));
    }

    private void givenReloadable(ContentGeneration generation) {
        when(generationRepository.findById(generation.getId())).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("폴링 주기는 scan-millis(기본 60초) fixedDelay로 설정된다")
    void pollsEveryMinute() throws NoSuchMethodException {
        Method method = GenerationDeadlineScheduler.class.getMethod("pollQueuedGenerations");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${ai.generation.scan-millis:60000}");
    }

    @Test
    @DisplayName("QUEUED generation이 없으면 AI 상태 조회를 하지 않는다")
    void pollEmptyBatch() {
        givenQueued();

        scheduler.pollQueuedGenerations();

        verify(aiContentClient, never()).getJobStatus(any());
        verify(generationRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 상태가 DRAFT_READY이면 DRAFT_READY로 전이한다")
    void pollDraftReady() {
        ContentGeneration gen = withinDeadline(1L);
        givenQueued(gen);
        givenReloadable(gen);
        when(aiContentClient.getJobStatus("job-1")).thenReturn("DRAFT_READY");

        scheduler.pollQueuedGenerations();

        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.DRAFT_READY);
    }

    @Test
    @DisplayName("AI 상태가 FAILED이면 FAILED로 전이한다")
    void pollFailed() {
        ContentGeneration gen = withinDeadline(2L);
        givenQueued(gen);
        givenReloadable(gen);
        when(aiContentClient.getJobStatus("job-2")).thenReturn("FAILED");

        scheduler.pollQueuedGenerations();

        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(savedCaptor.getValue().getCompletedAt()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "ANALYZING", "EXTRACTING", "GENERATING_BACKGROUNDS",
        "COMPOSING", "VERIFYING", "RENDERING", "DELIVERING", "COMPLETED"})
    @DisplayName("31분 이내에 AI가 진행 중(또는 COMPLETED 콜백 대기)이면 QUEUED를 유지하고 다음 주기에 다시 조회한다")
    void pollInProgressKeepsQueued(String aiStatus) {
        ContentGeneration gen = withinDeadline(3L);
        givenQueued(gen);
        when(aiContentClient.getJobStatus("job-3")).thenReturn(aiStatus);

        scheduler.pollQueuedGenerations();

        verify(generationRepository, never()).save(any());
        assertThat(gen.getStatus()).isEqualTo(GenerationStatus.QUEUED);
    }

    @Test
    @DisplayName("31분이 지나도 AI가 진행 중이면 마지막으로 조회한 뒤 FAILED 처리한다")
    void overdueInProgressExpires() {
        ContentGeneration gen = overdue(4L);
        givenQueued(gen);
        givenReloadable(gen);
        when(aiContentClient.getJobStatus("job-4")).thenReturn("RENDERING");

        scheduler.pollQueuedGenerations();

        verify(aiContentClient).getJobStatus("job-4");
        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    @DisplayName("31분이 지났더라도 마지막 조회에서 DRAFT_READY면 실패시키지 않고 DRAFT_READY로 전이한다")
    void overdueButDraftReady() {
        ContentGeneration gen = overdue(5L);
        givenQueued(gen);
        givenReloadable(gen);
        when(aiContentClient.getJobStatus("job-5")).thenReturn("DRAFT_READY");

        scheduler.pollQueuedGenerations();

        verify(generationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.DRAFT_READY);
    }

    @Test
    @DisplayName("31분이 지났고 AI 상태 조회도 실패하면 FAILED 처리한다")
    void overdueAndFetchErrorExpires() {
        ContentGeneration gen = overdue(6L);
        givenQueued(gen);
        givenReloadable(gen);
        when(aiContentClient.getJobStatus("job-6")).thenThrow(new RuntimeException("AI 연결 실패"));

        scheduler.pollQueuedGenerations();

        assertThat(gen.getStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    @DisplayName("AI 상태 조회 실패 시 예외를 삼키고 다음 generation을 계속 처리한다")
    void pollExceptionIsSwallowed() {
        ContentGeneration gen1 = withinDeadline(1L);
        ContentGeneration gen2 = withinDeadline(2L);
        givenQueued(gen1, gen2);
        givenReloadable(gen2);
        when(aiContentClient.getJobStatus("job-1")).thenThrow(new RuntimeException("AI 연결 실패"));
        when(aiContentClient.getJobStatus("job-2")).thenReturn("DRAFT_READY");

        scheduler.pollQueuedGenerations();

        verify(generationRepository, times(1)).save(any());
        assertThat(gen1.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(gen2.getStatus()).isEqualTo(GenerationStatus.DRAFT_READY);
    }

    @Test
    @DisplayName("조회 사이에 콜백으로 COMPLETED가 된 건은 덮어쓰지 않는다")
    void doesNotOverwriteCompletedByCallback() {
        ContentGeneration snapshot = overdue(7L);
        ContentGeneration latest = overdue(7L);
        latest.complete("{}", "7");
        givenQueued(snapshot);
        when(generationRepository.findById(7L)).thenReturn(Optional.of(latest));
        when(aiContentClient.getJobStatus("job-7")).thenReturn("COMPLETED");

        scheduler.pollQueuedGenerations();

        verify(generationRepository, never()).save(any());
        assertThat(latest.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
    }

    @Test
    @DisplayName("1분 간격 폴링: 31분 이내에는 매 주기 조회만 하고, 데드라인이 지난 주기에 FAILED 처리된다")
    void pollsEachCycleUntilDeadline() {
        ContentGeneration gen = withinDeadline(8L);
        when(generationRepository.findAllByStatus(GenerationStatus.QUEUED))
            .thenAnswer(inv -> gen.getStatus() == GenerationStatus.QUEUED ? List.of(gen) : List.of());
        when(aiContentClient.getJobStatus("job-8")).thenReturn("COMPOSING");

        int cycles = 0;
        for (long elapsed = 60; elapsed <= DEADLINE_SECONDS; elapsed += 60) {
            ReflectionTestUtils.setField(gen, "requestedAt", LocalDateTime.now().minusSeconds(elapsed));
            scheduler.pollQueuedGenerations();
            cycles++;
        }
        assertThat(gen.getStatus()).isEqualTo(GenerationStatus.QUEUED);

        givenReloadable(gen);
        ReflectionTestUtils.setField(gen, "requestedAt", LocalDateTime.now().minusSeconds(DEADLINE_SECONDS + 59));
        scheduler.pollQueuedGenerations();
        cycles++;
        scheduler.pollQueuedGenerations();

        assertThat(gen.getStatus()).isEqualTo(GenerationStatus.FAILED);
        verify(aiContentClient, times(cycles)).getJobStatus("job-8");
    }
}
