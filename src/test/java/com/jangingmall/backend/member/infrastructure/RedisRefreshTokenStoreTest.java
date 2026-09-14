package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Captor
    private ArgumentCaptor<String> storedValueCaptor;

    private RedisRefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        refreshTokenStore = new RedisRefreshTokenStore(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storesOnlyHashAndMatchesThePresentedRefreshToken() {
        refreshTokenStore.save(1L, "raw-refresh-token", Duration.ofDays(7));

        verify(valueOperations).set(any(), storedValueCaptor.capture(), any());
        String storedHash = storedValueCaptor.getValue();
        assertThat(storedHash).isNotEqualTo("raw-refresh-token");

        when(valueOperations.get("member:refresh-token:1")).thenReturn(storedHash);

        assertThat(refreshTokenStore.matches(1L, "raw-refresh-token")).isTrue();
        assertThat(refreshTokenStore.matches(1L, "different-token")).isFalse();
    }
}
