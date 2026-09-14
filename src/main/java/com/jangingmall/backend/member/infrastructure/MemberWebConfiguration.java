package com.jangingmall.backend.member.infrastructure;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@ConditionalOnBean(StringRedisTemplate.class)
public class MemberWebConfiguration {
    @Bean
    public WebMvcConfigurer memberRateLimits(AuthRateLimiter limiter) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(limiter).addPathPatterns("/api/member/login","/api/member/signup",
                    "/api/member/email-verifications","/api/member/oauth2/exchange","/api/member/oauth2/complete-profile");
            }
        };
    }
}
