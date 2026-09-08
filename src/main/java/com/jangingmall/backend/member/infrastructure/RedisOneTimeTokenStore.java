package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.OneTimeTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class RedisOneTimeTokenStore implements OneTimeTokenStore {
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String issue(String purpose, String payload, Duration ttl) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(purpose, token), payload, ttl);
        return token;
    }

    @Override
    public Optional<String> consume(String purpose, String token) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(purpose, token)));
    }

    private String key(String purpose, String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "member:one-time:" + purpose + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
