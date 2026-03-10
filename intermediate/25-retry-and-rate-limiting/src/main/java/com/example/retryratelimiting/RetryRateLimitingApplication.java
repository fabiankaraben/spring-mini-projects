package com.example.retryratelimiting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Retry and Rate Limiting demo application.
 *
 * <p>This Spring Boot application demonstrates two complementary Resilience4j
 * fault-tolerance patterns applied to outbound HTTP calls to an upstream
 * weather service:
 *
 * <h2>Retry Pattern</h2>
 * <p>When an upstream call fails with a transient error (e.g., a 503 Service
 * Unavailable or a connection timeout), the {@code @Retry} annotation
 * automatically re-invokes the method up to a configured number of times with
 * an optional back-off delay between attempts. This handles brief network
 * hiccups without propagating errors to the client.
 *
 * <h2>Rate Limiter Pattern</h2>
 * <p>The {@code @RateLimiter} annotation limits how many calls our service can
 * make to the upstream API within a given time period. When the limit is
 * exceeded, calls are either queued (up to a timeout) or immediately rejected
 * with a {@link io.github.resilience4j.ratelimiter.RequestNotPermitted}
 * exception, which triggers the fallback method.
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *   Client ──► WeatherController ──► WeatherService ──► WeatherClient ──► Upstream API
 *                                         │                   │
 *                                  @RateLimiter          @Retry
 *                                  (protect upstream)    (handle transients)
 * </pre>
 *
 * <p>A status endpoint ({@code /api/resilience/status}) exposes the current
 * Retry and RateLimiter instance metrics so you can observe the patterns live.
 */
@SpringBootApplication
public class RetryRateLimitingApplication {

    /**
     * Main method – bootstraps the Spring application context.
     *
     * @param args command-line arguments (none required for this demo)
     */
    public static void main(String[] args) {
        SpringApplication.run(RetryRateLimitingApplication.class, args);
    }
}
