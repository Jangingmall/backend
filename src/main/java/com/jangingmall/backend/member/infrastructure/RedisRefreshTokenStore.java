package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

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
    public boolean rotate(Long memberId, String expectedToken, String replacementToken, Duration ttl) {
        var script = new DefaultRedisScript<Long>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);
        return Long.valueOf(1).equals(redisTemplate.execute(script, List.of(key(memberId)),
            hash(expectedToken), hash(replacementToken), Long.toString(ttl.toMillis())));
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
