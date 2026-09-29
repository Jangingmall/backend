package com.jangingmall.backend.smoke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DAST 테스트 계정 적재 검증 — Parameter Store에서 주입된 환경변수로
 * 로그인 API를 호출해 계정이 DB에 존재하는지 확인한다.
 *
 * 사용:
 *   DAST_BASE_URL=https://api.midam.store \
 *   DAST_USER_EMAIL=dast-user@midam.store \
 *   DAST_USER_PASSWORD=<pw> \
 *   DAST_ARTISAN_EMAIL=dast-artisan@midam.store \
 *   DAST_ARTISAN_PASSWORD=<pw> \
 *   DAST_ADMIN_EMAIL=dast-admin@midam.store \
 *   DAST_ADMIN_PASSWORD=<pw> \
 *   ./gradlew dastInfraTest
 */
@Tag("dast-infra")
class DastAccountVerifyTest {

    private static final String BASE_URL = System.getenv().getOrDefault("DAST_BASE_URL", "https://api.midam.store");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @ParameterizedTest(name = "{0} 계정이 DB에 적재되어 로그인에 성공한다")
    @DisplayName("DAST 테스트 계정 로그인 검증")
    @CsvSource({
        "USER,    DAST_USER_EMAIL,    DAST_USER_PASSWORD",
        "ARTISAN, DAST_ARTISAN_EMAIL, DAST_ARTISAN_PASSWORD",
        "ADMIN,   DAST_ADMIN_EMAIL,   DAST_ADMIN_PASSWORD"
    })
    void dastAccountLoginSucceeds(String role, String emailEnv, String passwordEnv) throws Exception {
        String email = System.getenv(emailEnv);
        String password = System.getenv(passwordEnv);

        assertThat(email).as("%s 환경변수 누락", emailEnv).isNotBlank();
        assertThat(password).as("%s 환경변수 누락", passwordEnv).isNotBlank();

        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/member/login"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
            .as("DAST %s 계정(%s) 로그인 HTTP 상태 코드", role, email)
            .isEqualTo(200);
    }
}
