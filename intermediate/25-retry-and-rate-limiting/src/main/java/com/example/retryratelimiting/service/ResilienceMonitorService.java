package com.example.retryratelimiting.service;

import com.example.retryratelimiting.domain.ResilienceStatus;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service that collects and exposes Resilience4j Retry and RateLimiter metrics.
 *
 * <p>Resilience4j maintains a registry for each type of pattern. This service
 * queries both registries and returns a unified list of {@link ResilienceStatus}
 * snapshots that the status endpoint ({@code GET /api/resilience/status}) can serve.
 *
 * <p>This is useful for real-time observability: you can hit the status endpoint
 * while the application is running to see how many permits are available in the
 * rate limiter, or how many retry attempts have succeeded or failed so far.
 *
 * <p>For deeper metrics (Micrometer counters, Prometheus scraping), see the
 * Actuator endpoints exposed at {@code /actuator/retryevents} and
 * {@code /actuator/ratelimiters}.
 */
@Service
public class ResilienceMonitorService {

    /**
     * Resilience4j registry that holds all registered Retry instances.
     * Auto-configured by the {@code resilience4j-spring-boot3} starter.
     */
    private final RetryRegistry retryRegistry;

    /**
     * Resilience4j registry that holds all registered RateLimiter instances.
     * Auto-configured by the {@code resilience4j-spring-boot3} starter.
     */
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Constructor injection — both registries are beans provided by the Resilience4j
     * Spring Boot starter auto-configuration.
     *
     * @param retryRegistry       registry containing all @Retry instances
     * @param rateLimiterRegistry registry containing all @RateLimiter instances
     */
    public ResilienceMonitorService(RetryRegistry retryRegistry,
                                    RateLimiterRegistry rateLimiterRegistry) {
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    /**
     * Collect a status snapshot for every registered Retry and RateLimiter instance.
     *
     * <p>The method iterates over all instances in both registries and maps
     * their live metrics to {@link ResilienceStatus} records. The result
     * combines both types in a single list so callers get a unified view.
     *
     * @return list of status snapshots — one per Retry instance + one per RateLimiter instance
     */
    public List<ResilienceStatus> getAllStatuses() {
        List<ResilienceStatus> statuses = new ArrayList<>();

        // ── Retry instances ───────────────────────────────────────────────────────
        // Iterate over every Retry instance registered in the Resilience4j registry.
        // The registry is populated at startup from the application.yml configuration.
        retryRegistry.getAllRetries().forEach(retry -> {
            // Snapshot the live metrics for this Retry instance
            io.github.resilience4j.retry.Retry.Metrics metrics = retry.getMetrics();
            statuses.add(new ResilienceStatus(
                    "RETRY",
                    retry.getName(),
                    // RateLimiter permits don't apply to Retry — use sentinel -1
                    -1,
                    // Calls that succeeded on the first attempt (no retry needed)
                    metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                    // Calls that eventually succeeded after one or more retries
                    metrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                    // Calls that failed on the first attempt with no retries configured/triggered
                    metrics.getNumberOfFailedCallsWithoutRetryAttempt(),
                    // Calls that failed even after exhausting all retry attempts
                    metrics.getNumberOfFailedCallsWithRetryAttempt()
            ));
        });

        // ── RateLimiter instances ─────────────────────────────────────────────────
        // Iterate over every RateLimiter instance registered in the registry.
        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            // Snapshot the live metrics for this RateLimiter instance
            io.github.resilience4j.ratelimiter.RateLimiter.Metrics metrics = rl.getMetrics();
            statuses.add(new ResilienceStatus(
                    "RATE_LIMITER",
                    rl.getName(),
                    // Number of permits currently available in the current window
                    metrics.getAvailablePermissions(),
                    // Retry-specific metrics don't apply to RateLimiter — use 0
                    0,
                    0,
                    0,
                    0
            ));
        });

        return statuses;
    }

    /**
     * Collect a status snapshot for a single named Retry instance.
     *
     * @param name the instance name configured in application.yml
     * @return a {@link ResilienceStatus} for the Retry instance
     * @throws io.github.resilience4j.core.registry.EntryNotFoundException
     *         if no Retry with the given name is registered
     */
    public ResilienceStatus getRetryStatus(String name) {
        // Use find() which returns Optional – avoids auto-creating a new instance for unknown names.
        // orElseThrow() raises NoSuchElementException, which GlobalExceptionHandler maps to 404.
        io.github.resilience4j.retry.Retry retry = retryRegistry.find(name)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "No Retry instance with name '" + name + "' found in the registry"));
        io.github.resilience4j.retry.Retry.Metrics metrics = retry.getMetrics();
        return new ResilienceStatus(
                "RETRY",
                retry.getName(),
                -1,
                metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                metrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                metrics.getNumberOfFailedCallsWithoutRetryAttempt(),
                metrics.getNumberOfFailedCallsWithRetryAttempt()
        );
    }

    /**
     * Collect a status snapshot for a single named RateLimiter instance.
     *
     * @param name the instance name configured in application.yml
     * @return a {@link ResilienceStatus} for the RateLimiter instance
     * @throws java.util.NoSuchElementException if no RateLimiter with the given name is registered
     */
    public ResilienceStatus getRateLimiterStatus(String name) {
        // Use find() which returns Optional – avoids auto-creating a new instance for unknown names.
        // orElseThrow() raises NoSuchElementException, which GlobalExceptionHandler maps to 404.
        io.github.resilience4j.ratelimiter.RateLimiter rl = rateLimiterRegistry.find(name)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "No RateLimiter instance with name '" + name + "' found in the registry"));
        io.github.resilience4j.ratelimiter.RateLimiter.Metrics metrics = rl.getMetrics();
        return new ResilienceStatus(
                "RATE_LIMITER",
                rl.getName(),
                metrics.getAvailablePermissions(),
                0, 0, 0, 0
        );
    }
}
