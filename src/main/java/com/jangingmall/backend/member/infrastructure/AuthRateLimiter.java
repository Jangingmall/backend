package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class AuthRateLimiter implements HandlerInterceptor {
    private final StringRedisTemplate redis;
    private final int attempts;
    private final long windowSeconds;

    public AuthRateLimiter(StringRedisTemplate redis,@Value("${member.rate-limit.attempts:10}") int attempts,
                           @Value("${member.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redis=redis;
        this.attempts=attempts;
        this.windowSeconds=windowSeconds;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String bucket=hash(request.getRemoteAddr()+":"+request.getRequestURI());
        var script=new DefaultRedisScript<Long>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",Long.class);
        Long count=redis.execute(script,List.of("member:rate:"+bucket),Long.toString(windowSeconds));
        if (count==null || count>attempts) {
            response.setHeader("Retry-After",Long.toString(windowSeconds));
            throw new DomainException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
