package com.example.retryratelimiting.service;

import com.example.retryratelimiting.client.WeatherClient;
import com.example.retryratelimiting.domain.WeatherReport;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WeatherService}.
 *
 * <p>These tests verify the service's business logic and fallback behaviour in
 * pure isolation — no Spring context, no Resilience4j AOP proxy, no HTTP calls.
 *
 * <p><strong>Testing strategy:</strong>
 * <ul>
 *   <li>{@link WeatherClient} is replaced with a Mockito mock so no real HTTP
 *       requests are made. Tests run in milliseconds without any network dependency.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>Fallback methods are tested directly (package-private visibility) to verify
 *       the degraded response values without needing to trigger Resilience4j's AOP
 *       state machine (which requires a real Spring context).</li>
 * </ul>
 *
 * <p><strong>Why test fallbacks directly?</strong>
 * The Resilience4j annotations ({@code @Retry}, {@code @RateLimiter}) are AOP
 * interceptors — they only activate when the bean is invoked through the Spring proxy.
 * In a pure Mockito test, the service is a plain object (no proxy), so the annotations
 * have no effect. Testing fallback methods directly ensures their logic and return
 * values are correct without requiring an integration test setup.
 * The Retry and RateLimiter state transitions themselves are tested in the integration tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherService unit tests")
class WeatherServiceTest {

    /**
     * Mockito mock of the HTTP client.
     * No Spring context, no real HTTP calls — all interactions simulated via
     * {@code when(...).thenReturn(...)}.
     */
    @Mock
    private WeatherClient weatherClient;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a {@link WeatherService} instance and injects
     * the {@code @Mock} field via constructor injection.
     */
    @InjectMocks
    private WeatherService weatherService;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A sample WeatherReport returned by the mock client. */
    private WeatherReport sampleReport;

    @BeforeEach
    void setUp() {
        sampleReport = new WeatherReport(
                "London",
                "Partly cloudy",
                15.0,
                75,
                12.0,
                false,
                Instant.parse("2024-06-01T09:00:00Z")
        );
    }

    // ── getWeatherByCity – happy path ─────────────────────────────────────────────

    @Test
    @DisplayName("getWeatherByCity returns the report from the weather client when the call succeeds")
    void getWeatherByCity_returnsReport_whenClientSucceeds() {
        // Given: the mock client returns the sample report for "London"
        when(weatherClient.getWeatherByCity("London")).thenReturn(sampleReport);

        // When: invoke the service method directly (no AOP proxy in unit test)
        WeatherReport result = weatherService.getWeatherByCity("London");

        // Then: the service returns the report unchanged
        assertThat(result).isNotNull();
        assertThat(result.city()).isEqualTo("London");
        assertThat(result.description()).isEqualTo("Partly cloudy");
        assertThat(result.temperatureC()).isEqualTo(15.0);
        assertThat(result.humidity()).isEqualTo(75);
        assertThat(result.cached()).isFalse();

        // Verify the client was called exactly once with the correct city
        verify(weatherClient, times(1)).getWeatherByCity("London");
    }

    @Test
    @DisplayName("getWeatherByCity propagates exception from the weather client (no Resilience4j in unit test)")
    void getWeatherByCity_propagatesException_whenClientFails() {
        // Given: the mock client simulates a connection failure
        when(weatherClient.getWeatherByCity("Tokyo"))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // When / Then: without the AOP proxy, the raw exception propagates.
        // The @Retry and @RateLimiter decorators are NOT active in a plain Mockito test.
        assertThatThrownBy(() -> weatherService.getWeatherByCity("Tokyo"))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("Connection refused");

        verify(weatherClient, times(1)).getWeatherByCity("Tokyo");
    }

    @Test
    @DisplayName("getWeatherByCity passes the city parameter to the client unchanged")
    void getWeatherByCity_passesCityToClient_unchanged() {
        // Given
        String city = "New York";
        when(weatherClient.getWeatherByCity(city)).thenReturn(sampleReport);

        // When
        weatherService.getWeatherByCity(city);

        // Then: the exact city string is forwarded to the client
        verify(weatherClient, times(1)).getWeatherByCity("New York");
    }

    // ── getWeatherByCityFallback – triggered by Retry exhaustion ─────────────────

    @Test
    @DisplayName("getWeatherByCityFallback returns a synthetic report with cached=true")
    void getWeatherByCityFallback_returnsCachedReport_onRetryExhaustion() {
        // Given: a simulated exception (what @Retry would pass to the fallback after exhaustion)
        RuntimeException cause = new ResourceAccessException("upstream timeout");

        // When: invoke the fallback method directly (package-private access in same package)
        WeatherReport fallback = weatherService.getWeatherByCityFallback("Berlin", cause);

        // Then: the fallback report signals degraded mode via cached=true
        assertThat(fallback).isNotNull();
        // The city is preserved so callers know which city's data is unavailable
        assertThat(fallback.city()).isEqualTo("Berlin");
        assertThat(fallback.cached()).isTrue();
        assertThat(fallback.description()).contains("temporarily unavailable");
        // Null fields indicate no live data was available
        assertThat(fallback.temperatureC()).isNull();
        assertThat(fallback.humidity()).isNull();
        assertThat(fallback.windSpeedKmh()).isNull();
        // retrievedAt should be set so clients can see when the fallback was synthesised
        assertThat(fallback.retrievedAt()).isNotNull();
    }

    @Test
    @DisplayName("getWeatherByCityFallback preserves the city for any city name")
    void getWeatherByCityFallback_preservesCity_forAnyCity() {
        // Given: a rate-limiter rejection exception
        RequestNotPermitted rateLimitEx = RequestNotPermitted.createRequestNotPermitted(
                io.github.resilience4j.ratelimiter.RateLimiter.of("test",
                        RateLimiterConfig.ofDefaults()));

        // When
        WeatherReport fallback = weatherService.getWeatherByCityFallback("Sydney", rateLimitEx);

        // Then: city is correctly preserved regardless of the exception type
        assertThat(fallback.city()).isEqualTo("Sydney");
        assertThat(fallback.cached()).isTrue();
    }

    @Test
    @DisplayName("getWeatherByCityFallback works for any exception type including RequestNotPermitted")
    void getWeatherByCityFallback_worksForRequestNotPermitted() {
        // Given: RequestNotPermitted is the exception thrown by @RateLimiter when
        // no permit is available and the timeout-duration has elapsed
        RequestNotPermitted rateLimitEx = RequestNotPermitted.createRequestNotPermitted(
                io.github.resilience4j.ratelimiter.RateLimiter.of("test",
                        RateLimiterConfig.ofDefaults()));

        // When: invoke the fallback with a rate limiter rejection
        WeatherReport fallback = weatherService.getWeatherByCityFallback("Paris", rateLimitEx);

        // Then: fallback returns a safe synthetic report regardless of exception type
        assertThat(fallback.city()).isEqualTo("Paris");
        assertThat(fallback.cached()).isTrue();
        assertThat(fallback.temperatureC()).isNull();
    }

    @Test
    @DisplayName("getWeatherByCityFallback sets a non-null retrievedAt timestamp")
    void getWeatherByCityFallback_setsRetrievedAt() {
        // Given
        Instant before = Instant.now();
        RuntimeException cause = new RuntimeException("service down");

        // When
        WeatherReport fallback = weatherService.getWeatherByCityFallback("Rome", cause);

        // Then: retrievedAt should be close to "now" (within a few seconds)
        Instant after = Instant.now();
        assertThat(fallback.retrievedAt()).isBetween(before, after);
    }

    // ── INSTANCE_NAME constant ────────────────────────────────────────────────────

    @Test
    @DisplayName("INSTANCE_NAME constant matches the expected Resilience4j instance name")
    void instanceName_matchesExpectedName() {
        // This test guards against accidental renames that would break the
        // @Retry(name=INSTANCE_NAME) and @RateLimiter(name=INSTANCE_NAME) binding
        // to the application.yml configuration.
        assertThat(WeatherService.INSTANCE_NAME).isEqualTo("weatherService");
    }
}
