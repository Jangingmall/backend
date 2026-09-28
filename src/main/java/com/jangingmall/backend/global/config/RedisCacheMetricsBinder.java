package com.jangingmall.backend.global.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Collection;

public class RedisCacheMetricsBinder implements MeterBinder {

    private final RedisCacheManager cacheManager;

    public RedisCacheMetricsBinder(RedisCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            RedisCache cache = (RedisCache) cacheManager.getCache(cacheName);
            if (cache == null) {
                continue;
            }

            Gauge.builder("cache_gets_total", cache, c -> c.getStatistics().getHits())
                .tag("cache", cacheName)
                .tag("result", "hit")
                .description("캐시 히트 수")
                .register(registry);

            Gauge.builder("cache_gets_total", cache, c -> c.getStatistics().getMisses())
                .tag("cache", cacheName)
                .tag("result", "miss")
                .description("캐시 미스 수")
                .register(registry);

            Gauge.builder("cache_puts_total", cache, c -> c.getStatistics().getPuts())
                .tag("cache", cacheName)
                .description("캐시 put 수")
                .register(registry);
        }
    }
}
