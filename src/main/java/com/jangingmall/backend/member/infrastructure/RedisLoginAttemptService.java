package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.LoginAttemptService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    @Override
    public void recordFailure(String email) {
        String key = attemptKey(email);
        var script = new DefaultRedisScript<Long>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
            Long.class);
        redis.execute(script, List.of(key), Long.toString(LOCK_DURATION.getSeconds()));
    }

    @Override
    public void clearFailures(String email) {
        redis.delete(attemptKey(email));
    }

    @Override
    public boolean isLocked(String email) {
        String count = redis.opsForValue().get(attemptKey(email));
        if (count == null) {
            return false;
        }
        return Long.parseLong(count) >= MAX_ATTEMPTS;
    }

    private String attemptKey(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(email.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return "member:login-attempt:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
