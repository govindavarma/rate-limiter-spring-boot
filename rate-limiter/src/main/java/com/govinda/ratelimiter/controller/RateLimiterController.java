package com.govinda.ratelimiter.controller;

import com.govinda.ratelimiter.model.RateLimitResponse;
import com.govinda.ratelimiter.service.RateLimiterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for the Rate Limiter API.
 *
 * Endpoints:
 *   POST /api/request          — simulate a request (main rate-limit check)
 *   GET  /api/status/{clientId} — check token status for a client
 *   GET  /api/health            — health check
 *   GET  /api/config            — show current rate limit config
 */
@RestController
@RequestMapping("/api")
public class RateLimiterController {
    private final RateLimiterService rateLimiterService;

    public RateLimiterController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Main endpoint — simulates a rate-limited API request.
     * Pass clientId as a header (e.g. X-Client-Id: client_123) or query param.
     *
     * Returns 200 OK if allowed, 429 Too Many Requests if limit exceeded.
     */
    @PostMapping("/request")
    public ResponseEntity<RateLimitResponse> handleRequest(
            @RequestHeader(value = "X-Client-Id", defaultValue = "default-client") String clientId) {

        boolean allowed = rateLimiterService.isAllowed(clientId);
        int remaining = rateLimiterService.getRemainingTokens(clientId);
        long retryAfter = rateLimiterService.getRetryAfterSeconds(clientId);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(rateLimiterService.getMaxTokens()));
        headers.add("X-RateLimit-Remaining", String.valueOf(remaining));
        headers.add("X-RateLimit-Window", rateLimiterService.getWindowSeconds() + "s");

        if (allowed) {
            RateLimitResponse response = new RateLimitResponse(
                    true,
                    remaining,
                    0,
                    "Request accepted",
                    clientId
            );
            return ResponseEntity.ok().headers(headers).body(response);
        } else {
            // RFC 6585 — 429 Too Many Requests with Retry-After header
            headers.add("Retry-After", String.valueOf(retryAfter));

            RateLimitResponse response = new RateLimitResponse(
                    false,
                    0,
                    retryAfter,
                    "Rate limit exceeded. You have used all " + rateLimiterService.getMaxTokens()
                            + " requests. Try again in " + retryAfter + " seconds.",
                    clientId
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(response);
        }
    }
    @GetMapping("/status/{clientId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String clientId) {
        int remaining = rateLimiterService.getRemainingTokens(clientId);
        long retryAfter = rateLimiterService.getRetryAfterSeconds(clientId);

        Map<String, Object> status = new HashMap<>();
        status.put("clientId", clientId);
        status.put("remainingTokens", remaining);
        status.put("maxTokens", rateLimiterService.getMaxTokens());
        status.put("windowSeconds", rateLimiterService.getWindowSeconds());
        status.put("retryAfterSeconds", retryAfter);

        return ResponseEntity.ok(status);
    }
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "rate-limiter");
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the current rate limit configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxTokensPerClient", rateLimiterService.getMaxTokens());
        config.put("windowSeconds", rateLimiterService.getWindowSeconds());
        config.put("algorithm", "token-bucket");
        config.put("storage", "Redis");
        return ResponseEntity.ok(config);
    }
}
