package com.chubby.dolphin.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Rate Limiter Service.
 *
 * Implements a primary distributed Redis rate limiter for clustered production environments.
 * If Redis is unavailable (e.g., during local development), it automatically falls back
 * to a localized Bucket4j Token Bucket rate limiter to ensure system resilience.
 */
@Service
@Slf4j
public class RateLimiterService {

    public enum LimitType { AI, PAYMENT, GENERAL, LOGIN, WEBHOOK }

    private final StringRedisTemplate redisTemplate;
    
    // Fallback in-memory buckets
    private final Map<String, Bucket> aiBuckets  = new ConcurrentHashMap<>();
    private final Map<String, Bucket> paymentBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> webhookBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Returns true if the request is allowed, false if rate-limited */
    public boolean isAllowed(String userId, LimitType type) {
        try {
            return isAllowedRedis(userId, type);
        } catch (Exception e) {
            log.warn("⚠️ Redis rate limiter unavailable — falling back to local Bucket4j: {}", e.getMessage());
            return isAllowedLocal(userId, type);
        }
    }

    private boolean isAllowedRedis(String userId, LimitType type) {
        String key = "ratelimit:" + type.name().toLowerCase() + ":" + userId;
        long limit = switch (type) {
            case AI      -> 10;
            case PAYMENT -> 5;
            case GENERAL -> 60;
            case LOGIN   -> 5;
            case WEBHOOK -> 100;
        };
        long windowSeconds = 60;

        String currentVal = redisTemplate.opsForValue().get(key);
        long count = currentVal != null ? Long.parseLong(currentVal) : 0;

        if (count >= limit) {
            return false;
        }

        // Increment count and set TTL
        redisTemplate.opsForValue().increment(key);
        if (count == 0) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return true;
    }

    private boolean isAllowedLocal(String userId, LimitType type) {
        return getLocalBucket(userId, type).tryConsume(1);
    }

    private Bucket getLocalBucket(String userId, LimitType type) {
        return switch (type) {
            case AI      -> aiBuckets .computeIfAbsent(userId, k -> buildBucket(10, 60));
            case PAYMENT -> paymentBuckets.computeIfAbsent(userId, k -> buildBucket(5,  60));
            case GENERAL -> generalBuckets.computeIfAbsent(userId, k -> buildBucket(60, 60));
            case LOGIN   -> loginBuckets  .computeIfAbsent(userId, k -> buildBucket(5,  60));
            case WEBHOOK -> webhookBuckets.computeIfAbsent(userId, k -> buildBucket(100, 60));
        };
    }

    private Bucket buildBucket(long capacity, long refillSeconds) {
        Bandwidth limit = Bandwidth.classic(
            capacity,
            Refill.intervally(capacity, Duration.ofSeconds(refillSeconds))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /** Clear rate limit buckets for a user */
    public void clearUser(String userId) {
        try {
            for (LimitType type : LimitType.values()) {
                String key = "ratelimit:" + type.name().toLowerCase() + ":" + userId;
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("Failed to clear Redis rate limits for user: {}", e.getMessage());
        }
        aiBuckets.remove(userId);
        paymentBuckets.remove(userId);
        generalBuckets.remove(userId);
        loginBuckets.remove(userId);
        webhookBuckets.remove(userId);
    }
}
