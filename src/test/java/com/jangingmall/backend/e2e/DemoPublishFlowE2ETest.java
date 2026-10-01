package com.jangingmall.backend.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jangingmall.backend.content.application.ContentCommand;
import com.jangingmall.backend.content.application.ContentService;
import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.infrastructure.EmailSenderService;
import com.jangingmall.backend.payment.infrastructure.TossPaymentsGateway;
import com.jangingmall.backend.revalidate.application.RevalidateWebhookDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시연 시나리오 장면 1 → 장면 2의 판매자-소비자 연동: 판매자가 상품을 등록하고(FE가 보내는 최소 필드) AI 상세페이지를 제작 완료하면
 * 소비자 상세에서 판매 중으로 보이고 AI 이미지가 나와야 한다.
 */
@Tag("e2e")
@ActiveProfiles("local-postgresql")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=-1", "spring.sql.init.mode=never"})
class DemoPublishFlowE2ETest {

    private static final Long ARTISAN_ID = 1L;
    private static final String AI_IMAGE = "https://img.stg.midam.store/ai-generated/777/photo-1-hero.webp";

    @LocalServerPort
    private int port;
    @MockitoBean
    private EmailSenderService emailSenderService;
    @MockitoBean
    private AiContentClient aiContentClient;
    @MockitoBean
    private TossPaymentsGateway paymentGateway;
    @MockitoBean
    private RevalidateWebhookDispatcher revalidateWebhookDispatcher;

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private ContentService contentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("판매자가 상세페이지를 제작 완료하면 소비자 상세에서 판매 중으로 보이고 AI 이미지가 나온다")
    void publishedProductIsVisibleToConsumers() throws Exception {
        String artisanToken = new JwtTokenProvider(jwtProperties).createAccessToken(ARTISAN_ID, MemberRole.ARTISAN);

        // 1) 상품 등록 — FE 는 작품명·가격·재고만 보낸다
        HttpResponse<String> created = send("POST", "/api/products",
                Map.of("title", "시연 청자 찻잔", "price", 85000, "stock", 5), artisanToken);
        assertThat(created.statusCode()).isEqualTo(201);
        long productId = json(created).path("data").path("productId").asLong();
        assertThat(json(created).path("data").path("status").asText()).isEqualTo("DRAFT");

        // 2) AI 가 상세페이지 초안을 돌려줬다고 가정(콜백이 저장하는 것과 같은 경로)
        String document = "{\"root\":[{\"tag\":\"h2\",\"text\":\"청자 찻잔\"},"
                + "{\"tag\":\"img\",\"props\":{\"imageId\":\"hero\",\"src\":\"" + AI_IMAGE + "\"}},"
                + "{\"tag\":\"p\",\"text\":\"비색이 도는 찻잔입니다.\"}]}";
        contentService.storeReactDocument(new ContentCommand.StoreReactDocument(productId, document, null));

        // 3) 판매자가 검토 → 제작 완료
        String base = "/api/content/products/" + productId;
        long contentId = json(send("GET", base + "/contents", null, artisanToken)).path("data").path("contentId").asLong();
        assertThat(send("POST", base + "/contents/" + contentId + "/submit", null, artisanToken).statusCode()).isEqualTo(200);
        assertThat(send("POST", base + "/contents/" + contentId + "/approve",
                Map.of("factCheckConfirmed", true, "photoMatchConfirmed", true, "displayApprovalBadge", false),
                artisanToken).statusCode()).isEqualTo(200);
        assertThat(send("POST", base + "/publish", null, artisanToken).statusCode()).isEqualTo(200);

        // 4) 소비자(비로그인) 상세 — ON_SALE, 상세 이미지 블록, 대표 이미지
        JsonNode detail = json(send("GET", "/api/products/" + productId, null, null)).path("data");
        assertThat(detail.path("status").asText()).isEqualTo("ON_SALE");
        assertThat(detail.path("thumbnailUrl").asText()).isEqualTo(AI_IMAGE);
        JsonNode image = null;
        for (JsonNode block : detail.path("detailPageBlocks")) {
            if ("img".equals(block.path("tag").asText())) {
                image = block;
            }
        }
        assertThat(image).as("AI 이미지 블록").isNotNull();
        assertThat(image.path("hasImage").asBoolean()).isTrue();
        assertThat(image.path("imageVariants").get(0).path("url").asText()).isEqualTo(AI_IMAGE);
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
