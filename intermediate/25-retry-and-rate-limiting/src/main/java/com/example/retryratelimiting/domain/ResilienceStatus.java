package com.example.retryratelimiting.domain;

/**
 * Immutable domain record representing a snapshot of a single Resilience4j
 * instance (Retry or RateLimiter) at a point in time.
 *
 * <p>This is used by the {@code /api/resilience/status} endpoint to expose
 * the current state and metrics of each registered instance so callers can
 * observe Resilience4j behaviour in real time.
 *
 * @param type             the kind of pattern, e.g. "RETRY" or "RATE_LIMITER"
 * @param name             the configured instance name (e.g. "weatherService")
 * @param availablePermissions number of permits currently available in the rate limiter
 *                         (only meaningful for {@code RATE_LIMITER} type; -1 for others)
 * @param numberOfSuccessfulCallsWithoutRetryAttempt
 *                         total successful calls that did NOT need a retry attempt
 * @param numberOfSuccessfulCallsWithRetryAttempt
 *                         total successful calls that required at least one retry
 * @param numberOfFailedCallsWithoutRetryAttempt
 *                         total calls that failed on the first attempt with no further retry
 * @param numberOfFailedCallsWithRetryAttempt
 *                         total calls that still failed after all retries were exhausted
 */
public record ResilienceStatus(
        String type,
        String name,
        int availablePermissions,
        long numberOfSuccessfulCallsWithoutRetryAttempt,
        long numberOfSuccessfulCallsWithRetryAttempt,
        long numberOfFailedCallsWithoutRetryAttempt,
        long numberOfFailedCallsWithRetryAttempt
) {
}
