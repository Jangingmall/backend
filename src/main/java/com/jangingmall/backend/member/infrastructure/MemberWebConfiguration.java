package com.jangingmall.backend.member.infrastructure;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@ConditionalOnBean(StringRedisTemplate.class)
public class MemberWebConfiguration {

    @Bean
    public RedisTemplate<String, OAuth2AuthorizationRequest> oauth2AuthorizationRequestRedisTemplate(
        RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OAuth2AuthorizationRequest> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        return template;
    }

    @Bean
    public WebMvcConfigurer memberRateLimits(AuthRateLimiter limiter) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(limiter).addPathPatterns("/api/member/login", "/api/member/signup",
                    "/api/member/oauth2/exchange", "/api/member/oauth2/complete-profile");
            }
        };
    }
}
