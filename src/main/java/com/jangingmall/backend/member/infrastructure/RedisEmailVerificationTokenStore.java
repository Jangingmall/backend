package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.EmailVerificationTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Repository
public class RedisEmailVerificationTokenStore implements EmailVerificationTokenStore {

    private static final String MEMBER_KEY_PREFIX = "member:email-verification:member:";
    private static final String TOKEN_KEY_PREFIX = "member:email-verification:token:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisEmailVerificationTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String issue(Long memberId, Duration ttl) {
        String token = generateToken();
        String tokenHash = hash(token);
        String memberKey = memberKey(memberId);

        var script = new DefaultRedisScript<Long>("""
            local previous = redis.call('GET', KEYS[1])
            if previous then redis.call('DEL', ARGV[4] .. previous) end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);
        redisTemplate.execute(script, List.of(memberKey, tokenKey(tokenHash)), tokenHash, memberId.toString(),
            Long.toString(ttl.toMillis()), TOKEN_KEY_PREFIX);
        return token;
    }

    @Override
    public Optional<Long> consume(String token) {
        String tokenHash = hash(token);
        var script = new DefaultRedisScript<String>("""
            local member = redis.call('GET', KEYS[1])
            if not member then return nil end
            local memberKey = ARGV[2] .. member
            if redis.call('GET', memberKey) ~= ARGV[1] then
              redis.call('DEL', KEYS[1])
              return nil
            end
            redis.call('DEL', KEYS[1], memberKey)
            return member
            """, String.class);
        return Optional.ofNullable(redisTemplate.execute(script, List.of(tokenKey(tokenHash)),tokenHash,MEMBER_KEY_PREFIX))
            .map(Long::valueOf);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String memberKey(Long memberId) {
        return MEMBER_KEY_PREFIX + memberId;
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
