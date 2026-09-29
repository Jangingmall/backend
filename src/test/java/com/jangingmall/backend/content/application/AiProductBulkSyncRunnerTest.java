package com.jangingmall.backend.content.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiProductBulkSyncRunnerTest {

    @Mock
    ContentService contentService;

    @Mock
    ApplicationContext applicationContext;

    AiProductBulkSyncRunner runner;

    List<Integer> capturedExitCodes;

    @BeforeEach
    void setUp() {
        runner = new AiProductBulkSyncRunner(contentService, applicationContext);
        capturedExitCodes = new ArrayList<>();
        ReflectionTestUtils.setField(runner, "exitFn", (java.util.function.IntConsumer) capturedExitCodes::add);
    }

    @Test
    @DisplayName("일괄 동기화 성공 — bulkSyncPublishedProductsToAi를 호출하고 프로세스를 종료한다")
    void run_callsBulkSyncAndExits() throws Exception {
        when(contentService.bulkSyncPublishedProductsToAi()).thenReturn(5);

        runner.run(null);

        verify(contentService).bulkSyncPublishedProductsToAi();
        assertThat(capturedExitCodes).hasSize(1);
    }

    @Test
    @DisplayName("일괄 동기화 부분 실패 — synced < total이어도 프로세스를 종료한다")
    void run_partialFailure_stillExits() throws Exception {
        when(contentService.bulkSyncPublishedProductsToAi()).thenReturn(3);

        runner.run(null);

        verify(contentService).bulkSyncPublishedProductsToAi();
        assertThat(capturedExitCodes).hasSize(1);
    }
}
