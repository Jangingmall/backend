package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationTokenStoreTest {

    private static final String MEMBER_KEY = "member:email-verification:member:1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<String> hashCaptor;

    private RedisEmailVerificationTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new RedisEmailVerificationTokenStore(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("원문 인증 토큰 대신 해시와 회원 ID만 Redis에 저장한다")
    void issuesHashedToken() {
        Duration ttl = Duration.ofMinutes(30);
        when(valueOperations.get(MEMBER_KEY)).thenReturn(null);

        String token = tokenStore.issue(1L, ttl);

        verify(valueOperations).set(eq(MEMBER_KEY), hashCaptor.capture(), eq(ttl));
        String tokenHash = hashCaptor.getValue();
        assertThat(tokenHash).isNotEqualTo(token);
        verify(valueOperations).set(
            "member:email-verification:token:" + tokenHash,
            "1",
            ttl
        );
    }

    @Test
    @DisplayName("현재 발급된 토큰은 소비 후 양방향 키를 삭제한다")
    void consumesCurrentToken() {
        String token = "verification-token";
        String tokenHash = hash(token);
        String tokenKey = "member:email-verification:token:" + tokenHash;
        when(valueOperations.get(tokenKey)).thenReturn("1");
        when(valueOperations.get(MEMBER_KEY)).thenReturn(tokenHash);

        assertThat(tokenStore.consume(token)).contains(1L);

        verify(redisTemplate).delete(List.of(tokenKey, MEMBER_KEY));
    }

    @Test
    @DisplayName("재발급으로 교체된 이전 토큰은 더 이상 사용할 수 없다")
    void rejectsReplacedToken() {
        String token = "old-token";
        String tokenHash = hash(token);
        String tokenKey = "member:email-verification:token:" + tokenHash;
        when(valueOperations.get(tokenKey)).thenReturn("1");
        when(valueOperations.get(MEMBER_KEY)).thenReturn(hash("new-token"));

        assertThat(tokenStore.consume(token)).isEmpty();

        verify(redisTemplate).delete(tokenKey);
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
