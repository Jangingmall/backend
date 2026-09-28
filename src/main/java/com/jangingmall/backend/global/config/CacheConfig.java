package com.jangingmall.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJacksonJsonRedisSerializer(objectMapper)));

        Map<String, RedisCacheConfiguration> configs = Map.of(
            "products", defaults.entryTtl(Duration.ofSeconds(60)),
            "categories", defaults.entryTtl(Duration.ofSeconds(300))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(configs)
            .enableStatistics()
            .build();
    }

    @Bean
    public RedisCacheMetricsBinder redisCacheMetricsBinder(RedisCacheManager cacheManager) {
        return new RedisCacheMetricsBinder(cacheManager);
    }
}
