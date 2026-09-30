package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRenderApprovalAsyncExecutorTest {

    @Mock
    private AiContentClient aiContentClient;

    @Mock
    private ContentGenerationRepository generationRepository;

    private AiRenderApprovalAsyncExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new AiRenderApprovalAsyncExecutor(aiContentClient, generationRepository);
    }

    private ContentGeneration draftReady(Long id) {
        ContentGeneration generation = ContentGeneration.create(10L, "img", "상품명", "과정", "관리");
        generation.markQueued("job-" + id, "req-" + id, id.toString(), "http://ai/status/" + id);
        generation.markDraftReady();
        ReflectionTestUtils.setField(generation, "id", id);
        return generation;
    }

    private void givenClaimed(Long id) {
        when(generationRepository.claimRender(eq(id), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("DRAFT_READY 건은 선점에 성공하면 jobId·generationId·productId로 렌더링을 요청한다")
    void requestsRenderForDraftReady() {
        givenClaimed(1L);
        when(generationRepository.findById(1L)).thenReturn(Optional.of(draftReady(1L)));

        executor.requestRender(1L);

        verify(aiContentClient).approveRender("job-1", 1L, 10L);
    }

    @Test
    @DisplayName("콘텐츠 승인·수동 요청 이벤트도 같은 경로로 렌더링을 요청한다")
    void eventRequestsRender() {
        givenClaimed(2L);
        when(generationRepository.findById(2L)).thenReturn(Optional.of(draftReady(2L)));

        executor.onRenderApprovalRequested(new AiRenderApprovalRequestedEvent("job-2", 2L));

        verify(aiContentClient).approveRender("job-2", 2L, 10L);
    }

    @Test
    @DisplayName("다른 요청(다른 인스턴스 포함)이 이미 선점했거나 DRAFT_READY가 아니면 렌더링을 요청하지 않는다")
    void skipsWhenNotClaimed() {
        when(generationRepository.claimRender(eq(3L), any(), any())).thenReturn(false);

        executor.requestRender(3L);

        verify(generationRepository, never()).findById(any());
        verify(aiContentClient, never()).approveRender(any(), any(), any());
    }

    @Test
    @DisplayName("선점했더라도 generation이 없으면 렌더링을 요청하지 않는다")
    void skipsWhenMissing() {
        givenClaimed(4L);
        when(generationRepository.findById(4L)).thenReturn(Optional.empty());

        executor.requestRender(4L);

        verify(aiContentClient, never()).approveRender(any(), any(), any());
    }

    @Test
    @DisplayName("자동 재시도는 선점 후 5분이 지난 건만 다시 요청하고, 수동 요청은 선점 기간을 무시한다")
    void claimLeaseDiffersBetweenAutomaticAndManual() {
        when(generationRepository.claimRender(eq(5L), any(), any())).thenReturn(false);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> staleBefore = ArgumentCaptor.forClass(LocalDateTime.class);

        executor.requestRender(5L);
        executor.onRenderApprovalRequested(new AiRenderApprovalRequestedEvent("job-5", 5L));

        verify(generationRepository, times(2)).claimRender(eq(5L), now.capture(), staleBefore.capture());
        Duration automatic = Duration.between(staleBefore.getAllValues().get(0), now.getAllValues().get(0));
        Duration manual = Duration.between(staleBefore.getAllValues().get(1), now.getAllValues().get(1));
        assertThat(automatic).isEqualTo(Duration.ofMinutes(AiRenderApprovalAsyncExecutor.CLAIM_LEASE_MINUTES));
        assertThat(manual).isZero();
    }

    @Test
    @DisplayName("렌더링 요청이 실패해도 예외를 전파하지 않고, 다음 요청은 다시 시도할 수 있다")
    void failureIsSwallowedAndRetryable() {
        givenClaimed(6L);
        when(generationRepository.findById(6L)).thenReturn(Optional.of(draftReady(6L)));
        doThrow(new IllegalStateException("AI 오류")).doNothing()
            .when(aiContentClient).approveRender("job-6", 6L, 10L);

        executor.requestRender(6L);
        executor.requestRender(6L);

        verify(aiContentClient, times(2)).approveRender("job-6", 6L, 10L);
    }

    @Test
    @DisplayName("같은 건의 렌더링 요청이 이 인스턴스에서 진행 중이면 중복 요청을 건너뛴다")
    void skipsDuplicateWhileInFlight() throws Exception {
        givenClaimed(7L);
        when(generationRepository.findById(7L)).thenReturn(Optional.of(draftReady(7L)));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(aiContentClient).approveRender("job-7", 7L, 10L);

        Thread first = new Thread(() -> executor.requestRender(7L));
        first.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        executor.requestRender(7L);
        release.countDown();
        first.join(5_000);

        assertThat(calls.get()).isEqualTo(1);
    }
}
