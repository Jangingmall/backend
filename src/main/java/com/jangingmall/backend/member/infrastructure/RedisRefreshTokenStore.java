package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "member:refresh-token:";
    private static final String SHARED_KEY_PREFIX = "member:refresh-tokens:";
    private static final long MAX_SHARED_TOKENS = 20;

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(Long memberId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(memberId), hash(refreshToken), ttl);
    }

    @Override
    public void saveShared(Long memberId, String refreshToken, Duration ttl) {
        String sharedKey = sharedKey(memberId);
        Long size = redisTemplate.opsForSet().size(sharedKey);
        if (size != null && size >= MAX_SHARED_TOKENS) {
            redisTemplate.delete(sharedKey);
        }
        redisTemplate.opsForSet().add(sharedKey, hash(refreshToken));
        redisTemplate.expire(sharedKey, ttl);
    }

    @Override
    public boolean matches(Long memberId, String refreshToken) {
        String presented = hash(refreshToken);
        String storedHash = redisTemplate.opsForValue().get(key(memberId));
        if (storedHash != null && MessageDigest.isEqual(
            storedHash.getBytes(StandardCharsets.UTF_8),
            presented.getBytes(StandardCharsets.UTF_8)
        )) {
            return true;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(sharedKey(memberId), presented));
    }

    @Override
    public void delete(Long memberId) {
        redisTemplate.delete(List.of(key(memberId), sharedKey(memberId)));
    }

    private String sharedKey(Long memberId) {
        return SHARED_KEY_PREFIX + memberId;
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
