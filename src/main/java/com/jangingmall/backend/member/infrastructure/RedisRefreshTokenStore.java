package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "member:refresh-token:";

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(Long memberId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(memberId), hash(refreshToken), ttl);
    }

    @Override
    public boolean matches(Long memberId, String refreshToken) {
        String storedHash = redisTemplate.opsForValue().get(key(memberId));
        return storedHash != null && MessageDigest.isEqual(
            storedHash.getBytes(StandardCharsets.UTF_8),
            hash(refreshToken).getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public void delete(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }

    private String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
