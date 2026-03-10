package com.example.retryratelimiting.service;

import com.example.retryratelimiting.client.WeatherClient;
import com.example.retryratelimiting.domain.WeatherReport;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service that fetches weather data from the upstream weather API and applies
 * Resilience4j Retry and RateLimiter fault-tolerance patterns.
 *
 * <h2>Retry Pattern</h2>
 * <p>The {@code @Retry} annotation wraps the outbound HTTP call and automatically
 * re-invokes the method when a transient exception is thrown (e.g., a 503 Service
 * Unavailable or a connection timeout). Key concepts:
 * <ul>
 *   <li><em>max-attempts</em> – total number of attempts including the first call.
 *       With 3 attempts, the method can be called up to 3 times total before
 *       the exception propagates (or the fallback is invoked).</li>
 *   <li><em>wait-duration</em> – fixed or exponential back-off between retries.
 *       This avoids hammering a struggling upstream service.</li>
 *   <li><em>retry-exceptions</em> – only exceptions listed here trigger a retry;
 *       others propagate immediately. This prevents retrying on bad input (4xx).</li>
 * </ul>
 *
 * <h2>Rate Limiter Pattern</h2>
 * <p>The {@code @RateLimiter} annotation limits how many calls can be made to the
 * upstream API within a fixed time window. Key concepts:
 * <ul>
 *   <li><em>limit-for-period</em> – maximum calls allowed per refresh period.</li>
 *   <li><em>limit-refresh-period</em> – window duration after which the counter resets.</li>
 *   <li><em>timeout-duration</em> – how long a call waits for a permit before being
 *       rejected with {@link RequestNotPermitted}.</li>
 * </ul>
 *
 * <h2>Decorator order</h2>
 * <p>Resilience4j applies decorators from outermost to innermost:
 * {@code RateLimiter → Retry → actual call}.
 * This means:
 * <ol>
 *   <li>The RateLimiter gate is checked first — if no permit is available within
 *       the timeout, the call is rejected immediately (no retry attempt).</li>
 *   <li>If a permit is granted, the Retry decorator takes over and may re-invoke
 *       the method up to {@code max-attempts} times on transient failures.</li>
 * </ol>
 *
 * <h2>Fallback Methods</h2>
 * <p>Both annotations specify a {@code fallbackMethod} that is invoked when:
 * <ul>
 *   <li>The RateLimiter rejects the call ({@link RequestNotPermitted}).</li>
 *   <li>The Retry exhausts all attempts and the exception still propagates.</li>
 * </ul>
 * The fallback returns a synthetic {@link WeatherReport} marked with
 * {@code cached=true} so clients can detect degraded mode.
 *
 * <h2>Configuration</h2>
 * <p>All thresholds and window sizes are configured in {@code application.yml}
 * under {@code resilience4j.retry.instances.weatherService} and
 * {@code resilience4j.ratelimiter.instances.weatherService}.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    /**
     * Name constant for both the Retry and RateLimiter instances.
     * Must match the key under {@code resilience4j.retry.instances} and
     * {@code resilience4j.ratelimiter.instances} in application.yml.
     * Using a constant avoids typos in both annotations and tests.
     */
    public static final String INSTANCE_NAME = "weatherService";

    /** HTTP client that calls the upstream weather API. */
    private final WeatherClient weatherClient;

    /**
     * Constructor injection — makes the dependency explicit and allows
     * replacing the real HTTP client with a mock in unit tests.
     *
     * @param weatherClient the HTTP client for the upstream weather API
     */
    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    // ── getWeatherByCity ──────────────────────────────────────────────────────────

    /**
     * Fetch the current weather report for the given city from the upstream API.
     *
     * <p>Fault-tolerance stack (outermost → innermost):
     * <ol>
     *   <li>{@code @RateLimiter} – checks if the call is permitted based on the
     *       configured rate limit. If no permit is available within
     *       {@code timeout-duration}, throws {@link RequestNotPermitted} and
     *       routes to {@link #getWeatherByCityFallback}.</li>
     *   <li>{@code @Retry} – if the upstream call throws a retryable exception,
     *       re-invokes the method up to {@code max-attempts} times with back-off.
     *       After all retries fail, routes to {@link #getWeatherByCityFallback}.</li>
     * </ol>
     *
     * @param city the city name (e.g., "London", "New York")
     * @return the live weather report, or a fallback report if rate-limited or upstream fails
     */
    @RateLimiter(name = INSTANCE_NAME, fallbackMethod = "getWeatherByCityFallback")
    @Retry(name = INSTANCE_NAME, fallbackMethod = "getWeatherByCityFallback")
    public WeatherReport getWeatherByCity(String city) {
        log.info("Fetching weather for city '{}' from upstream weather API", city);
        return weatherClient.getWeatherByCity(city);
    }

    /**
     * Fallback for {@link #getWeatherByCity}.
     *
     * <p>This method is invoked when:
     * <ul>
     *   <li>The {@code @RateLimiter} rejects the call (too many requests).</li>
     *   <li>The {@code @Retry} exhausts all attempts after upstream failures.</li>
     * </ul>
     *
     * <p><strong>Fallback naming contract:</strong> the method name is passed as
     * {@code fallbackMethod} in both annotations. The signature must match the
     * primary method's signature with an extra {@link Throwable} parameter added
     * at the end. Resilience4j selects the best matching overload at runtime.
     *
     * <p>The returned report has {@code cached=true} so clients can detect that
     * they are receiving a degraded/synthetic response, not live data.
     *
     * @param city      the city that was requested
     * @param throwable the exception that triggered the fallback:
     *                  {@link RequestNotPermitted} for rate-limited calls,
     *                  or a {@link org.springframework.web.client.RestClientException}
     *                  subclass for exhausted retries
     * @return a synthetic fallback WeatherReport
     */
    WeatherReport getWeatherByCityFallback(String city, Throwable throwable) {
        // Differentiate the reason for the fallback in the log so operators can diagnose
        if (throwable instanceof RequestNotPermitted) {
            log.warn("Rate limiter rejected request for city '{}': too many requests", city);
        } else {
            log.warn("Retry exhausted for city '{}', using fallback. Cause: {}",
                    city, throwable.getMessage());
        }
        // Return a safe synthetic report – marked cached=true to signal degraded mode
        return new WeatherReport(
                city,
                "Weather data temporarily unavailable",
                null,   // temperature unknown in fallback
                null,   // humidity unknown in fallback
                null,   // wind speed unknown in fallback
                true,   // cached=true signals this is NOT live data
                Instant.now()
        );
    }
}
