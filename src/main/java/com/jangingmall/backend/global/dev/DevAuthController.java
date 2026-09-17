package com.jangingmall.backend.global.dev;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Profile({"local", "local-postgresql"})
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevAuthController {

    private static final long DEV_TOKEN_EXPIRY_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final String DEV_ARTISAN_EMAIL = "dev-artisan@midam.store";
    private static final String DEV_PRODUCT_TITLE = "[DEV] 청자 다완 테스트 상품";

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/token")
    public ApiResponse<DevTokenResponse> token(
        @RequestParam(defaultValue = "ARTISAN") String role,
        @RequestParam(required = false) Long memberId
    ) {
        MemberRole memberRole = MemberRole.valueOf(role.toUpperCase());
        long resolvedId = memberId != null ? memberId : switch (memberRole) {
            case ADMIN -> -2L;
            default    -> -3L;
        };
        String accessToken = jwtTokenProvider.createAccessToken(resolvedId, memberRole, DEV_TOKEN_EXPIRY_MILLIS);
        return ApiResponse.ok(new DevTokenResponse(accessToken, memberRole.name(), resolvedId));
    }

    @PostMapping("/setup")
    @Transactional
    public ApiResponse<DevSetupResponse> setup() {
        Long artisanId = findOrCreateDevArtisan();
        Long productId = findOrCreateDevProduct(artisanId);
        String accessToken = jwtTokenProvider.createAccessToken(artisanId, MemberRole.ARTISAN, DEV_TOKEN_EXPIRY_MILLIS);
        return ApiResponse.ok(new DevSetupResponse(artisanId, productId, accessToken));
    }

    private Long findOrCreateDevArtisan() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT member_id FROM member WHERE email = ?", DEV_ARTISAN_EMAIL
        );
        if (!rows.isEmpty()) {
            return toLong(rows.get(0).get("member_id"));
        }
        LocalDateTime now = LocalDateTime.now();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO member (email, password_hash, name, nickname, phone, role, status," +
                " age14_or_older, terms_agreed, privacy_agreed, marketing_agreed, created_at, updated_at)" +
                " VALUES (?, 'dev-no-password', 'Dev Artisan', 'dev-artisan', '010-0000-0000'," +
                " 'ARTISAN', 'ACTIVE', true, true, true, false, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, DEV_ARTISAN_EMAIL);
            ps.setObject(2, now);
            ps.setObject(3, now);
            return ps;
        }, keyHolder);
        Long memberId = toLong(keyHolder.getKey());
        jdbcTemplate.update(
            "INSERT INTO artisan_profile (artisan_id, business_name, introduction, certification_level, is_organization, certification_status, popularity_score, updated_at)" +
            " VALUES (?, 'Dev 공방', '개발 테스트용 공방', '일반', false, 'APPROVED', 0, ?)",
            memberId, now
        );
        return memberId;
    }

    private Long findOrCreateDevProduct(Long artisanId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT product_id FROM product WHERE artisan_id = ? AND title = ?", artisanId, DEV_PRODUCT_TITLE
        );
        if (!rows.isEmpty()) {
            return toLong(rows.get(0).get("product_id"));
        }
        LocalDateTime now = LocalDateTime.now();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO product (artisan_id, title, description, price, stock, status, is_limited, is_custom_order, is_single_item, has_gift_wrap, created_at, updated_at)" +
                " VALUES (?, ?, '개발 테스트용 상품', 85000, 10, 'ON_SALE', false, false, false, false, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, artisanId);
            ps.setString(2, DEV_PRODUCT_TITLE);
            ps.setObject(3, now);
            ps.setObject(4, now);
            return ps;
        }, keyHolder);
        return toLong(keyHolder.getKey());
    }

    private Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        throw new IllegalStateException("키 타입 변환 실패: " + value);
    }

    public record DevTokenResponse(String accessToken, String role, Long memberId) {}

    public record DevSetupResponse(Long artisanId, Long productId, String accessToken) {}
}
