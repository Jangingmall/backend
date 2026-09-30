package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.LoginAttemptService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisLoginAttemptService implements LoginAttemptService {

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration lockDuration;
    private final DastLoginLockExemption dastExemption;

    public RedisLoginAttemptService(
        StringRedisTemplate redis,
        DastLoginLockExemption dastExemption,
        @Value("${member.login-lock.max-attempts:5}") int maxAttempts,
        @Value("${member.login-lock.lock-minutes:30}") long lockMinutes
    ) {
        this.redis = redis;
        this.dastExemption = dastExemption;
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    @Override
    public void recordFailure(String email) {
        if (dastExemption.isExempt(email)) {
            return;
        }
        String key = attemptKey(email);
        var script = new DefaultRedisScript<Long>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
            Long.class);
        redis.execute(script, List.of(key), Long.toString(lockDuration.getSeconds()));
    }

    @Override
    public void clearFailures(String email) {
        redis.delete(attemptKey(email));
    }

    @Override
    public boolean isLocked(String email) {
        if (dastExemption.isExempt(email)) {
            return false;
        }
        String count = redis.opsForValue().get(attemptKey(email));
        if (count == null) {
            return false;
        }
        return Long.parseLong(count) >= maxAttempts;
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
