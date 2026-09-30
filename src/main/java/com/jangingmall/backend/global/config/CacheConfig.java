package com.jangingmall.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
@EnableCaching
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private record CacheWrapper(String type, Object payload) {}

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper cacheMapper
    ) {
        RedisSerializer<Object> serializer = new RedisSerializer<>() {

            @Override
            public byte[] serialize(Object value) {
                if (value == null) {
                    return new byte[0];
                }

                try {
                    return cacheMapper.writeValueAsBytes(
                            new CacheWrapper(
                                    value.getClass().getName(),
                                    value
                            )
                    );
                } catch (Exception e) {
                    throw new SerializationException(
                            "직렬화 실패: type=" + value.getClass().getName(),
                            e
                    );
                }
            }

            @Override
            public Object deserialize(byte[] bytes) {
                if (bytes == null || bytes.length == 0) {
                    return null;
                }

                try {
                    CacheWrapper wrapper =
                            cacheMapper.readValue(bytes, CacheWrapper.class);

                    Class<?> type = Class.forName(wrapper.type());

                    return cacheMapper.convertValue(
                            wrapper.payload(),
                            type
                    );
                } catch (Exception e) {
                    throw new SerializationException(
                            "역직렬화 실패",
                            e
                    );
                }
            }
        };

        RedisCacheConfiguration defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(60))
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(new StringRedisSerializer())
                        )
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(serializer)
                        );

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
}