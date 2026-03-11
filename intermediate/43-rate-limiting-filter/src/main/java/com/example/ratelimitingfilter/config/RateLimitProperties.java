package com.example.ratelimitingfilter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration properties for the rate limiter.
 *
 * <p>Spring Boot binds the {@code app.rate-limit.*} keys from
 * {@code application.yml} (or environment variables) to the fields of this
 * class at startup. Using a dedicated properties class instead of scattering
 * {@code @Value} annotations makes the configuration self-documenting and easy
 * to validate.
 *
 * <p>Example YAML:
 * <pre>
 * app:
 *   rate-limit:
 *     capacity: 10
 *     refill-period-seconds: 60
 *     key-prefix: "rate_limit:"
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /**
     * Maximum number of tokens (requests) the bucket can hold.
     * This is also the number of requests allowed per {@link #refillPeriodSeconds}.
     */
    private int capacity = 10;

    /**
     * Length of the replenishment window in seconds.
     * When this period elapses the bucket is fully refilled to {@link #capacity}.
     */
    private long refillPeriodSeconds = 60;

    /**
     * Redis key prefix used to namespace token-bucket entries.
     * The full key will be {@code <keyPrefix><clientIdentifier>}.
     */
    private String keyPrefix = "rate_limit:";

    // ── Getters and setters ────────────────────────────────────────────────────

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getRefillPeriodSeconds() {
        return refillPeriodSeconds;
    }

    public void setRefillPeriodSeconds(long refillPeriodSeconds) {
        this.refillPeriodSeconds = refillPeriodSeconds;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
