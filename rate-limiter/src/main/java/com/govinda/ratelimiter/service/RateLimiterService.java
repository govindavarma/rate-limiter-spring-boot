package com.govinda.ratelimiter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Token Bucket Rate Limiter using Redis.
 *
 * How it works:
 * - Each client gets a "bucket" stored in Redis with a token count.
 * - Every request consumes 1 token from that client's bucket.
 * - Tokens refill completely after the time window expires (Redis TTL handles this).
 * - If tokens reach 0, the request is rejected with HTTP 429 Too Many Requests.
 * - The Retry-After header tells the client how many seconds to wait before retrying.
 */
@Service
public class RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rate.limiter.max-tokens}")
    private int maxTokens;

    @Value("${rate.limiter.window-seconds}")
    private long windowSeconds;

    private static final String KEY_PREFIX = "rate_limit:";

    public RateLimiterService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if a request from the given clientId is allowed.
     *
     * @param clientId unique identifier for the client (e.g. IP address, API key)
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;

        // Get current token count from Redis
        String currentValue = redisTemplate.opsForValue().get(key);

        if (currentValue == null) {
            // First request from this client — create bucket with max tokens, consume 1
            redisTemplate.opsForValue().set(key, String.valueOf(maxTokens - 1), Duration.ofSeconds(windowSeconds));
            return true;
        }

        int currentTokens = Integer.parseInt(currentValue);

        if (currentTokens <= 0) {
            // Bucket is empty — reject request
            return false;
        }

        // Consume 1 token — decrement atomically
        redisTemplate.opsForValue().decrement(key);
        return true;
    }

    /**
     * Get the remaining token count for a client.
     */
    public int getRemainingTokens(String clientId) {
        String key = KEY_PREFIX + clientId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return maxTokens;
        return Math.max(0, Integer.parseInt(value));
    }

    /**
     * Get how many seconds remain until the bucket refills (Redis TTL).
     */
    public long getRetryAfterSeconds(String clientId) {
        String key = KEY_PREFIX + clientId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : windowSeconds;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }
}
