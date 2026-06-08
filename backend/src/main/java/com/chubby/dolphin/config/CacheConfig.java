package com.chubby.dolphin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis Cache Configuration — only activates when spring.cache.type=redis
 * Falls back to simple in-memory cache when Redis is not running.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(Map.of(
                    "dashboard", base.entryTtl(Duration.ofSeconds(60)),
                    "campaigns", base.entryTtl(Duration.ofSeconds(30)),
                    "emas",      base.entryTtl(Duration.ofMinutes(5)),
                    "events",    base.entryTtl(Duration.ofSeconds(10)),
                    "wallet",    base.entryTtl(Duration.ofSeconds(30))
                ))
                .build();
    }
}
