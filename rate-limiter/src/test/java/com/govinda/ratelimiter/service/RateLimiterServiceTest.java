package com.govinda.ratelimiter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(rateLimiterService, "maxTokens", 10);
        ReflectionTestUtils.setField(rateLimiterService, "windowSeconds", 60L);
    }

    @Test
    void firstRequest_shouldBeAllowed() {
        // New client — no key in Redis yet
        when(valueOperations.get("rate_limit:client1")).thenReturn(null);

        boolean result = rateLimiterService.isAllowed("client1");

        assertTrue(result);
        verify(valueOperations).set(eq("rate_limit:client1"), eq("9"), any());
    }

    @Test
    void requestWithTokensAvailable_shouldBeAllowed() {
        when(valueOperations.get("rate_limit:client1")).thenReturn("5");

        boolean result = rateLimiterService.isAllowed("client1");

        assertTrue(result);
        verify(valueOperations).decrement("rate_limit:client1");
    }

    @Test
    void requestWithNoTokensLeft_shouldBeRejected() {
        when(valueOperations.get("rate_limit:client1")).thenReturn("0");

        boolean result = rateLimiterService.isAllowed("client1");

        assertFalse(result);
        verify(valueOperations, never()).decrement(any());
    }

    @Test
    void getRemainingTokens_noKey_returnsMax() {
        when(valueOperations.get("rate_limit:client1")).thenReturn(null);

        int remaining = rateLimiterService.getRemainingTokens("client1");

        assertEquals(10, remaining);
    }

    @Test
    void getRetryAfterSeconds_returnsTTL() {
        when(redisTemplate.getExpire("rate_limit:client1", TimeUnit.SECONDS)).thenReturn(45L);

        long retryAfter = rateLimiterService.getRetryAfterSeconds("client1");

        assertEquals(45L, retryAfter);
    }
}
