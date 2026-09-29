package com.jangingmall.backend.content.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.function.IntConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "job.ai-bulk-sync.enabled", havingValue = "true")
public class AiProductBulkSyncRunner implements ApplicationRunner {

    private final ContentService contentService;
    private final ApplicationContext applicationContext;

    IntConsumer exitFn = System::exit;

    @Override
    public void run(ApplicationArguments args) {
        log.info("AI 상품 일괄 동기화 Job 시작");
        int synced = contentService.bulkSyncPublishedProductsToAi();
        log.info("AI 상품 일괄 동기화 Job 완료 synced={}", synced);
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        exitFn.accept(exitCode);
    }
}
