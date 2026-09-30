package com.jangingmall.backend.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("============================================================");
        log.info("[prod-reset] DB 스키마 초기화 시작");
        log.info("============================================================");

        try {
            resetSchema();

            log.info("[prod-reset] DB 스키마 초기화 완료");
            log.info("[prod-reset] 초기화 완료 - 애플리케이션 종료");

            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        } catch (Exception e) {
            log.error("[prod-reset] DB 스키마 초기화 실패", e);

            int exitCode = SpringApplication.exit(applicationContext, () -> 1);
            System.exit(exitCode);
        }
    }

    private void resetSchema() {
        jdbcTemplate.execute("DROP SCHEMA public CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA public");

        jdbcTemplate.execute("GRANT ALL ON SCHEMA public TO pg_database_owner");
        jdbcTemplate.execute("GRANT USAGE ON SCHEMA public TO public");
        jdbcTemplate.execute("GRANT CREATE ON SCHEMA public TO public");

        log.info("[prod-reset] DROP SCHEMA public CASCADE / CREATE SCHEMA public 완료");
    }
}