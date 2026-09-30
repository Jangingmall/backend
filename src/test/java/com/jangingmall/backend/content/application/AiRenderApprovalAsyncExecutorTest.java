package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("DRAFT_READY 건은 jobId·generationId·productId로 렌더링을 요청한다")
    void requestsRenderForDraftReady() {
        when(generationRepository.findById(1L)).thenReturn(Optional.of(draftReady(1L)));

        executor.requestRender(1L);

        verify(aiContentClient).approveRender("job-1", 1L, 10L);
    }

    @Test
    @DisplayName("콘텐츠 승인 이벤트도 같은 경로로 렌더링을 요청한다")
    void eventRequestsRender() {
        when(generationRepository.findById(2L)).thenReturn(Optional.of(draftReady(2L)));

        executor.onRenderApprovalRequested(new AiRenderApprovalRequestedEvent("job-2", 2L));

        verify(aiContentClient).approveRender("job-2", 2L, 10L);
    }

    @Test
    @DisplayName("DRAFT_READY가 아닌 건(이미 완료·실패 등)은 렌더링을 요청하지 않는다")
    void skipsWhenNotDraftReady() {
        ContentGeneration completed = draftReady(3L);
        completed.complete("{}", "3");
        when(generationRepository.findById(3L)).thenReturn(Optional.of(completed));

        executor.requestRender(3L);

        verify(aiContentClient, never()).approveRender(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 generation이면 렌더링을 요청하지 않는다")
    void skipsWhenMissing() {
        when(generationRepository.findById(4L)).thenReturn(Optional.empty());

        executor.requestRender(4L);

        verify(aiContentClient, never()).approveRender(any(), any(), any());
    }

    @Test
    @DisplayName("렌더링 요청이 실패해도 예외를 전파하지 않고, 다음 요청은 다시 시도할 수 있다")
    void failureIsSwallowedAndRetryable() {
        when(generationRepository.findById(5L)).thenReturn(Optional.of(draftReady(5L)));
        doThrow(new IllegalStateException("AI 오류")).doNothing()
            .when(aiContentClient).approveRender("job-5", 5L, 10L);

        executor.requestRender(5L);
        executor.requestRender(5L);

        org.mockito.Mockito.verify(aiContentClient, org.mockito.Mockito.times(2))
            .approveRender("job-5", 5L, 10L);
    }

    @Test
    @DisplayName("같은 건의 렌더링 요청이 진행 중이면 중복 요청을 건너뛴다")
    void skipsDuplicateWhileInFlight() throws Exception {
        when(generationRepository.findById(6L)).thenReturn(Optional.of(draftReady(6L)));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(aiContentClient).approveRender("job-6", 6L, 10L);

        Thread first = new Thread(() -> executor.requestRender(6L));
        first.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        executor.requestRender(6L);
        release.countDown();
        first.join(5_000);

        assertThat(calls.get()).isEqualTo(1);
    }
}
