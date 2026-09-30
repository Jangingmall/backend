package com.jangingmall.backend.global.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * prod-reset 프로파일 전용: PostgreSQL public 스키마를 초기화하고 프로세스를 종료한다.
 * Flyway validation/migration이 실행되기 전에 스키마를 깨끗하게 비운다.
 * 일반 prod 프로파일에서는 절대 실행되지 않는다.
 */
@Slf4j
@Profile("prod-reset")
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DbResetRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("============================================================");
        log.info("[prod-reset] DB 스키마 초기화 시작");
        log.info("============================================================");

        try {
            dropAndCreateSchema();
            log.info("[prod-reset] DB 스키마 초기화 완료");
        } catch (Exception e) {
            log.error("[prod-reset] DB 스키마 초기화 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("DB 초기화 실패 - 수동 개입 필요", e);
        }

        log.info("============================================================");
        log.info("[prod-reset] 초기화 완료 - 애플리케이션 종료");
        log.info("============================================================");

        // 정상 종료 (exit code 0)
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void dropAndCreateSchema() {
        // public 스키마 전체 삭제 후 재생성
        // CASCADE로 모든 객체(테이블, 시퀀스, 뷰 등) 함께 삭제
        entityManager.createNativeQuery("DROP SCHEMA public CASCADE").executeUpdate();
        entityManager.createNativeQuery("CREATE SCHEMA public").executeUpdate();

        // 권한 재부여 (PostgreSQL 기본 권한 복원)
        entityManager.createNativeQuery("GRANT ALL ON SCHEMA public TO pg_database_owner").executeUpdate();
        entityManager.createNativeQuery("GRANT USAGE ON SCHEMA public TO public").executeUpdate();
        entityManager.createNativeQuery("GRANT CREATE ON SCHEMA public TO public").executeUpdate();

        log.info("[prod-reset] DROP SCHEMA public CASCADE / CREATE SCHEMA public 완료");
    }
}