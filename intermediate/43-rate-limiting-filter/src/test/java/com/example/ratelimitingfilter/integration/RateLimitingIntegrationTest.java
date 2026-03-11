package com.example.ratelimitingfilter.integration;

import com.example.ratelimitingfilter.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the rate-limiting interceptor.
 *
 * <p>These tests:
 * <ul>
 *   <li>Start a real Redis instance inside a Docker container via
 *       <strong>Testcontainers</strong>.</li>
 *   <li>Boot the full Spring application context ({@code @SpringBootTest}).</li>
 *   <li>Send HTTP requests through the entire MVC stack using
 *       {@link MockMvc} (no network overhead, but interceptors are invoked).</li>
 * </ul>
 *
 * <h2>Why a real Redis?</h2>
 * <p>Using a real Redis container (rather than mocking the template) ensures that
 * the persistence and TTL logic in {@link RateLimiterService} is tested against an
 * actual key-value store, catching issues like key expiry, data-type mismatches, or
 * serialisation problems.
 *
 * <h2>Test isolation</h2>
 * <p>Each test uses a different {@code X-Forwarded-For} header value so that the
 * rate-limit buckets are independent. The {@link BeforeEach} method additionally
 * resets the bucket for the shared test IP to guarantee a clean state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Rate Limiting Integration Tests (Testcontainers + Redis)")
class RateLimitingIntegrationTest {

    /** Official Redis Docker image to use for the test container. */
    private static final String REDIS_IMAGE = "redis:7-alpine";

    /** Standard Redis port inside the container. */
    private static final int REDIS_PORT = 6379;

    /**
     * Testcontainers-managed Redis container.
     *
     * <p>{@code @Container} on a static field means Testcontainers starts the
     * container once for the entire test class and stops it after all tests finish.
     * This is faster than restarting per test.
     */
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT);

    /**
     * Provides the Redis connection details (random host port assigned by
     * Testcontainers) to the Spring application context <em>before</em> the
     * context is created.
     *
     * <p>{@code @DynamicPropertySource} is the canonical Testcontainers + Spring
     * Boot integration point. It overrides the placeholder values in
     * {@code application-test.yml}.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiterService rateLimiterService;

    /**
     * Resets Redis state before each test to prevent cross-test contamination.
     *
     * <p>The test profile configures a capacity of 5. We reset the bucket for the
     * well-known "shared" IP used by MockMvc (127.0.0.1) so that each test starts
     * with a full bucket.
     */
    @BeforeEach
    void resetBuckets() {
        // Reset bucket for the default MockMvc remote address
        rateLimiterService.resetBucket("127.0.0.1");
        // Reset buckets for per-test IPs used throughout this class
        for (int i = 1; i <= 20; i++) {
            rateLimiterService.resetBucket("10.0.0." + i);
        }
    }

    // ── Happy-path tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/ping returns 200 with pong message")
    void pingReturns200() throws Exception {
        mockMvc.perform(get("/api/ping")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"));
    }

    @Test
    @DisplayName("GET /api/data returns 200 with expected fields")
    void dataReturns200() throws Exception {
        mockMvc.perform(get("/api/data")
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("sample-data"))
                .andExpect(jsonPath("$.value").value(42));
    }

    // ── Rate-limiting header tests ─────────────────────────────────────────────

    @Test
    @DisplayName("Response contains X-RateLimit-Limit header with configured capacity")
    void responseContainsRateLimitLimitHeader() throws Exception {
        mockMvc.perform(get("/api/ping")
                        .header("X-Forwarded-For", "10.0.0.3"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "5"));
    }

    @Test
    @DisplayName("Response contains X-RateLimit-Remaining header")
    void responseContainsRateLimitRemainingHeader() throws Exception {
        mockMvc.perform(get("/api/ping")
                        .header("X-Forwarded-For", "10.0.0.4"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("X-RateLimit-Remaining decreases with each successful request")
    void remainingHeaderDecreases() throws Exception {
        String clientIp = "10.0.0.5";

        // First request – 4 remaining (started at 5, consumed 1)
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "4"));

        // Second request – 3 remaining
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "3"));
    }

    // ── Rate-limit enforcement tests ───────────────────────────────────────────

    @Test
    @DisplayName("Returns 429 after exceeding the configured rate limit")
    void returns429AfterExceedingLimit() throws Exception {
        String clientIp = "10.0.0.6";

        // Exhaust all 5 tokens (capacity configured in application-test.yml)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp))
                    .andExpect(status().isOk());
        }

        // 6th request must be rate-limited
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("429 response includes Retry-After header")
    void rateLimitedResponseContainsRetryAfterHeader() throws Exception {
        String clientIp = "10.0.0.7";

        // Exhaust the bucket
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp));
        }

        // Rate-limited response must advertise the refill delay
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("429 response body contains meaningful error message")
    void rateLimitedResponseBodyContainsErrorMessage() throws Exception {
        String clientIp = "10.0.0.8";

        // Exhaust the bucket
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp));
        }

        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(containsString("Rate limit exceeded")));
    }

    // ── Isolation tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Different clients have independent token buckets")
    void differentClientsHaveIndependentBuckets() throws Exception {
        String clientA = "10.0.0.9";
        String clientB = "10.0.0.10";

        // Exhaust client A's bucket
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientA))
                    .andExpect(status().isOk());
        }

        // Client A is rate-limited
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientA))
                .andExpect(status().isTooManyRequests());

        // Client B is unaffected
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientB))
                .andExpect(status().isOk());
    }

    // ── Actuator exclusion test ────────────────────────────────────────────────

    @Test
    @DisplayName("Actuator /health endpoint is excluded from rate limiting")
    void actuatorHealthNotRateLimited() throws Exception {
        String clientIp = "10.0.0.11";

        // Exhaust the bucket for this IP
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/ping").header("X-Forwarded-For", clientIp));
        }

        // Actuator health check must still succeed even though the bucket is empty
        // (the interceptor excludes /actuator/**)
        mockMvc.perform(get("/actuator/health").header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk());
    }
}
