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

        String previousHash = redisTemplate.opsForValue().get(memberKey);
        if (previousHash != null) {
            redisTemplate.delete(tokenKey(previousHash));
        }

        redisTemplate.opsForValue().set(memberKey, tokenHash, ttl);
        redisTemplate.opsForValue().set(tokenKey(tokenHash), memberId.toString(), ttl);
        return token;
    }

    @Override
    public Optional<Long> consume(String token) {
        String tokenHash = hash(token);
        String tokenKey = tokenKey(tokenHash);
        String memberId = redisTemplate.opsForValue().get(tokenKey);
        if (memberId == null) {
            return Optional.empty();
        }

        String memberKey = memberKey(Long.valueOf(memberId));
        String currentHash = redisTemplate.opsForValue().get(memberKey);
        if (!MessageDigest.isEqual(
            tokenHash.getBytes(StandardCharsets.UTF_8),
            currentHash == null ? new byte[0] : currentHash.getBytes(StandardCharsets.UTF_8)
        )) {
            redisTemplate.delete(tokenKey);
            return Optional.empty();
        }

        redisTemplate.delete(List.of(tokenKey, memberKey));
        return Optional.of(Long.valueOf(memberId));
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
