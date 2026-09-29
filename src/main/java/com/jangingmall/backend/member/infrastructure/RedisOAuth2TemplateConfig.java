package com.jangingmall.backend.member.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@Configuration
public class RedisOAuth2TemplateConfig {

    @Bean
    public RedisTemplate<String, OAuth2AuthorizationRequest> oauth2AuthorizationRequestRedisTemplate(
        RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OAuth2AuthorizationRequest> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        return template;
    }
}
