package com.jangingmall.backend.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.notification.application.NotificationCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=-1")
class NotificationCrudIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProperties jwtProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String userToken;
    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        userToken = jwtTokenProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        return newClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + path))
            .GET();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return newClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String token) throws Exception {
        return newClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        return newClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .DELETE()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private Long createNotification() throws Exception {
        HttpResponse<String> res = post("/api/notifications",
            new NotificationCreateRequest("테스트 알림", "내용입니다"), userToken);
        assertThat(res.statusCode()).isEqualTo(201);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(res.body(), Map.class).get("data");
        return Long.valueOf(data.get("id").toString());
    }

    @Test
    @DisplayName("알림 CRUD — 생성 후 목록 조회 시 포함된다")
    void createAndList() throws Exception {
        createNotification();

        HttpResponse<String> res = get("/api/notifications", userToken);
        assertThat(res.statusCode()).isEqualTo(200);
        List<?> items = (List<?>) objectMapper.readValue(res.body(), Map.class).get("data");
        assertThat(items).isNotEmpty();
    }

    @Test
    @DisplayName("알림 읽음 처리 — 읽음 처리 후 unread-count가 감소한다")
    void markAsReadDecrementsUnreadCount() throws Exception {
        createNotification();
        createNotification();

        HttpResponse<String> countRes = get("/api/notifications/unread-count", userToken);
        Map<?, ?> countData = (Map<?, ?>) objectMapper.readValue(countRes.body(), Map.class).get("data");
        int before = Integer.parseInt(countData.get("unreadCount").toString());
        assertThat(before).isGreaterThanOrEqualTo(2);

        HttpResponse<String> listRes = get("/api/notifications", userToken);
        List<?> items = (List<?>) objectMapper.readValue(listRes.body(), Map.class).get("data");
        Long firstId = Long.valueOf(((Map<?, ?>) items.get(0)).get("id").toString());

        patch("/api/notifications/" + firstId + "/read", userToken);

        HttpResponse<String> afterRes = get("/api/notifications/unread-count", userToken);
        Map<?, ?> afterData = (Map<?, ?>) objectMapper.readValue(afterRes.body(), Map.class).get("data");
        int after = Integer.parseInt(afterData.get("unreadCount").toString());
        assertThat(after).isEqualTo(before - 1);
    }

    @Test
    @DisplayName("전체 읽음 처리 — 처리 후 unread-count가 0이 된다")
    void markAllAsReadReturnsZero() throws Exception {
        createNotification();
        createNotification();

        patch("/api/notifications/read-all", userToken);

        HttpResponse<String> res = get("/api/notifications/unread-count", userToken);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(res.body(), Map.class).get("data");
        int count = Integer.parseInt(data.get("unreadCount").toString());
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("알림 삭제 — 삭제 후 단건 조회 시 status가 DELETED가 된다")
    void deleteNotificationChangesStatusToDeleted() throws Exception {
        Long notificationId = createNotification();

        HttpResponse<String> deleteRes = delete("/api/notifications/" + notificationId, userToken);
        assertThat(deleteRes.statusCode()).isEqualTo(200);

        HttpResponse<String> getRes = get("/api/notifications/" + notificationId, userToken);
        assertThat(getRes.statusCode()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(getRes.body(), Map.class).get("data");
        assertThat(data.get("status").toString()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("타 회원 알림 조회 — 접근하면 403을 반환한다")
    void getOtherMemberNotification() throws Exception {
        Long notificationId = createNotification();

        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        String otherToken = jwtTokenProvider.createAccessToken(999L, MemberRole.USER);

        HttpResponse<String> res = get("/api/notifications/" + notificationId, otherToken);
        assertThat(res.statusCode()).isEqualTo(403);
    }
}
