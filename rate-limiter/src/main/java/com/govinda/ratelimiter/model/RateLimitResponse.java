package com.govinda.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateLimitResponse {
    private boolean allowed;
    private int remainingTokens;
    private long retryAfterSeconds;
    private String message;
    private String clientId;
}
