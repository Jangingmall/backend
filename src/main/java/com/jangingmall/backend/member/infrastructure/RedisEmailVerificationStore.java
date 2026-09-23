package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.EmailVerificationStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationStore implements EmailVerificationStore {

    private final StringRedisTemplate redis;

    @Override
    public void save(String email, String code, Duration ttl) {
        redis.opsForValue().set(key(email), code, ttl);
    }

    @Override
    public Optional<String> consumeCode(String email) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(email)));
    }

    private String key(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return "member:email-verify:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
