package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationTokenStoreTest {

    private static final String MEMBER_KEY = "member:email-verification:member:1";

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisEmailVerificationTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new RedisEmailVerificationTokenStore(redisTemplate);
    }

    @Test
    @DisplayName("원문 인증 토큰 대신 해시와 회원 ID만 Redis에 저장한다")
    void issuesHashedToken() {
        Duration ttl = Duration.ofMinutes(30);
        String token = tokenStore.issue(1L, ttl);

        ArgumentCaptor<DefaultRedisScript<Long>> script=ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redisTemplate).execute(script.capture(), anyList(), any(), any(), any(), any());
        assertThat(script.getValue().getScriptAsString()).contains("previous").contains("PX");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("현재 발급된 토큰은 소비 후 양방향 키를 삭제한다")
    void consumesCurrentToken() {
        String token = "verification-token";
        String tokenHash = hash(token);
        String tokenKey = "member:email-verification:token:" + tokenHash;
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(tokenKey)), eq(tokenHash), eq("member:email-verification:member:")))
            .thenReturn("1");

        assertThat(tokenStore.consume(token)).contains(1L);

        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(tokenKey)), eq(tokenHash), eq("member:email-verification:member:"));
    }

    @Test
    @DisplayName("재발급으로 교체된 이전 토큰은 더 이상 사용할 수 없다")
    void rejectsReplacedToken() {
        String token = "old-token";
        String tokenHash = hash(token);
        String tokenKey = "member:email-verification:token:" + tokenHash;
        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(tokenKey)), eq(tokenHash), eq("member:email-verification:member:")))
            .thenReturn(null);

        assertThat(tokenStore.consume(token)).isEmpty();

        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(tokenKey)), eq(tokenHash), eq("member:email-verification:member:"));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
