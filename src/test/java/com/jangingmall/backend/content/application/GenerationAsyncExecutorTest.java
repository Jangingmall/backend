package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.AiJobAccepted;
import com.jangingmall.backend.content.domain.ContentGeneration;
import com.jangingmall.backend.content.domain.ContentGenerationRepository;
import com.jangingmall.backend.content.domain.GenerationStatus;
import com.jangingmall.backend.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationAsyncExecutorTest {

    @Mock
    private ContentGenerationRepository generationRepository;
    @Mock
    private AiContentClient aiContentClient;

    @Captor
    private ArgumentCaptor<ContentGeneration> generationCaptor;

    private GenerationAsyncExecutor executor;

    private static final AiJobAccepted ACCEPTED_JOB =
        new AiJobAccepted("job-123", "req-456", "http://ai/status/job-123");

    @BeforeEach
    void setUp() {
        executor = new GenerationAsyncExecutor(generationRepository, aiContentClient);
    }

    private GenerationCommand.Request sampleCommand() {
        return new GenerationCommand.Request(10L, 1L, List.of("img1"), "청자 다완", "손으로 빚음", "물 닦기");
    }

    @Test
    @DisplayName("AI job 제출 성공 시 QUEUED로 전환하고 jobId를 저장한다")
    void onGenerationRequestedSuccess() {
        ContentGeneration generation = ContentGeneration.create(10L, "img1", "청자 다완", "손으로 빚음", "물 닦기");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        when(aiContentClient.submitJob(any(), any(), any(), any(), any(), any())).thenReturn(ACCEPTED_JOB);

        executor.onGenerationRequested(new GenerationRequestedEvent(1L, sampleCommand()));

        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(generationCaptor.getValue().getJobId()).isEqualTo("job-123");
    }

    @Test
    @DisplayName("AI job 제출 실패 시 최대 2회 재시도 후 FAILED로 전환한다")
    void onGenerationRequestedRetryThenFail() {
        ContentGeneration generation = ContentGeneration.create(10L, "img1", "청자 다완", "손으로 빚음", "물 닦기");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        doThrow(new RuntimeException("AI 서버 오류")).when(aiContentClient)
            .submitJob(any(), any(), any(), any(), any(), any());

        executor.onGenerationRequested(new GenerationRequestedEvent(1L, sampleCommand()));

        verify(aiContentClient, times(3)).submitJob(any(), any(), any(), any(), any(), any());
        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    @DisplayName("AI job 제출 첫 번째 실패 후 재시도 성공 시 QUEUED로 전환한다")
    void onGenerationRequestedRetrySuccess() {
        ContentGeneration generation = ContentGeneration.create(10L, "img1", "청자 다완", "손으로 빚음", "물 닦기");
        ReflectionTestUtils.setField(generation, "id", 1L);
        when(generationRepository.findByIdAndProductId(1L, 10L)).thenReturn(Optional.of(generation));
        when(generationRepository.save(any())).thenReturn(generation);
        when(aiContentClient.submitJob(any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("1차 실패"))
            .thenReturn(ACCEPTED_JOB);

        executor.onGenerationRequested(new GenerationRequestedEvent(1L, sampleCommand()));

        verify(aiContentClient, times(2)).submitJob(any(), any(), any(), any(), any(), any());
        verify(generationRepository).save(generationCaptor.capture());
        assertThat(generationCaptor.getValue().getStatus()).isEqualTo(GenerationStatus.QUEUED);
    }

    @Test
    @DisplayName("존재하지 않는 generationId로 이벤트 발생 시 NotFoundException이 전파된다")
    void onGenerationRequestedNotFound() {
        when(generationRepository.findByIdAndProductId(999L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> executor.onGenerationRequested(new GenerationRequestedEvent(999L, sampleCommand()))
        ).isInstanceOf(NotFoundException.class);
    }
}
