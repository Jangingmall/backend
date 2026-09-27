package com.jangingmall.backend.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("prod-smoke")
@Testcontainers
@ActiveProfiles("local-postgresql")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "management.server.port=-1",
    }
)
class LocalPostgresqlSmokeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
        .withDatabaseName("jangingmall")
        .withUsername("jangingmall")
        .withPassword("jangingmall");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    @DisplayName("local-postgresql 프로필 — Testcontainer PostgreSQL로 ApplicationContext 기동이 성공한다")
    void localPostgresqlContextLoads() {
        assertThatCode(() -> {}).doesNotThrowAnyException();
    }
}
