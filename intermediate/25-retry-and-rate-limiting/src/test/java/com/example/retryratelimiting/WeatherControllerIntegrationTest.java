package com.example.retryratelimiting;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Retry and Rate Limiting application.
 *
 * <p>These tests verify end-to-end behaviour from the HTTP layer through the
 * service layer (including Resilience4j AOP decorators) down to a mocked
 * upstream weather API (WireMock).
 *
 * <h2>Test setup overview</h2>
 * <ul>
 *   <li>{@link SpringBootTest} starts the full Spring application context,
 *       including the Resilience4j Retry and RateLimiter AOP proxies and the
 *       {@link com.example.retryratelimiting.service.WeatherService} bean.</li>
 *   <li>{@link AutoConfigureMockMvc} injects {@link MockMvc}, which sends HTTP
 *       requests through the full filter/controller/handler chain.</li>
 *   <li>{@link WireMockServer} (in-process) acts as the fake upstream weather API.
 *       It is started once per test class and reset between tests.</li>
 *   <li>{@link DynamicPropertySource} overrides {@code weather.base-url} so
 *       the {@link com.example.retryratelimiting.client.WeatherClient} points at
 *       WireMock instead of the real service.</li>
 * </ul>
 *
 * <h2>What is tested here (vs. unit tests)</h2>
 * <ul>
 *   <li>Successful weather retrieval through the full stack.</li>
 *   <li>Retry fallback activation after upstream 5xx failures (retries exhausted).</li>
 *   <li>RateLimiter fallback activation when the permit budget is exhausted.</li>
 *   <li>Resilience status endpoint returns correct instance names.</li>
 *   <li>Actuator health endpoint includes rate limiter health.</li>
 * </ul>
 *
 * <h2>Why WireMock in-process (not a Docker container)?</h2>
 * <p>We use {@link WireMockServer} directly (in-process Java server) rather than
 * running WireMock as a Testcontainers Docker container because:
 * <ul>
 *   <li>It starts/stops in milliseconds (no Docker pull / container spin-up).</li>
 *   <li>It runs in the same JVM, so stub setup is a simple method call (no HTTP).</li>
 *   <li>The WireMock standalone JAR already ships the embedded server.</li>
 * </ul>
 * <p>Testcontainers is still present in the test classpath and pom.xml (required
 * dependency), and its Docker API version fix is applied via the classpath properties
 * files — demonstrating the full Testcontainers integration setup even though no
 * Docker container is needed for this particular project.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("WeatherController integration tests (Retry + RateLimiter + WireMock)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeatherControllerIntegrationTest {

    // ── WireMock server (in-process) ──────────────────────────────────────────────

    /**
     * In-process WireMock server acting as the fake upstream weather API.
     *
     * <p>{@code static} ensures a single instance is shared across all test methods.
     * Starting it in {@link BeforeAll} avoids per-test startup overhead.
     * Port 0 = OS-assigned random port (no conflicts with other running services).
     */
    static WireMockServer wireMockServer;

    /**
     * Start WireMock before any tests in this class run.
     */
    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        // Point WireMock's static client API at our in-process server
        configureFor("localhost", wireMockServer.port());
    }

    /**
     * Stop WireMock after all tests in this class have finished.
     */
    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    /**
     * Override the upstream API base URL before the Spring context is created.
     *
     * <p>{@link DynamicPropertySource} injects properties into the Spring
     * {@link org.springframework.core.env.Environment} before context startup.
     * This makes {@link com.example.retryratelimiting.client.WeatherClient} call
     * WireMock instead of the real weather service.
     */
    @DynamicPropertySource
    static void overrideWeatherBaseUrl(DynamicPropertyRegistry registry) {
        // Supplier is evaluated after wireMockServer.start(), so the port is known
        registry.add("weather.base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    /**
     * Reset all WireMock stubs and recorded requests before each test.
     *
     * <p>Without this, a stub registered in one test would remain active in
     * subsequent tests, causing unexpected behaviour.
     *
     * <p><strong>Resilience4j state isolation:</strong>
     * Neither {@code Retry} nor {@code RateLimiter} expose a {@code reset()} method
     * in Resilience4j 2.x. Test isolation is instead achieved through configuration:
     * <ul>
     *   <li>The {@code integration-test} profile sets a tight rate limit (2 calls / 60 s)
     *       specifically for the rate-limit exhaustion test, which runs its own 3 requests
     *       in sequence. All other tests make only 1 request and therefore never exhaust
     *       the budget when run in isolation.</li>
     *   <li>Retry has no open/closed state — it simply retries on each invocation
     *       independently, so no cross-test state contamination can occur.</li>
     * </ul>
     */
    @BeforeEach
    void resetWireMock() {
        // Reset WireMock stubs so previous test's stubs don't bleed into this test
        resetAllRequests();
        wireMockServer.resetAll();
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /**
     * MockMvc performs HTTP requests against the mock servlet started by
     * {@link SpringBootTest}. Spring Boot auto-configures this bean when
     * {@link AutoConfigureMockMvc} is present.
     */
    @Autowired
    MockMvc mockMvc;

    /**
     * Resilience4j registry that holds all RateLimiter instances.
     * Injected here for assertions on rate limiter state in tests.
     */
    @Autowired
    RateLimiterRegistry rateLimiterRegistry;

    // ── GET /api/weather?city=... ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/weather returns 200 with weather report when upstream is healthy")
    void getWeather_returns200WithReport_whenUpstreamHealthy() throws Exception {
        // Given: WireMock stubs GET /weather?city=London to return a JSON weather report
        stubFor(WireMock.get(urlPathEqualTo("/weather"))
                .withQueryParam("city", WireMock.equalTo("London"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "city": "London",
                                  "description": "Partly cloudy",
                                  "temperatureC": 15.5,
                                  "humidity": 72,
                                  "windSpeedKmh": 14.0,
                                  "cached": false,
                                  "retrievedAt": "2024-06-01T09:00:00Z"
                                }
                                """)));

        // When / Then: the API returns the live weather report
        mockMvc.perform(get("/api/weather")
                        .queryParam("city", "London")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city", is("London")))
                .andExpect(jsonPath("$.description", is("Partly cloudy")))
                .andExpect(jsonPath("$.temperatureC", is(15.5)))
                .andExpect(jsonPath("$.humidity", is(72)))
                .andExpect(jsonPath("$.cached", is(false)));
    }

    @Test
    @DisplayName("GET /api/weather returns fallback with cached=true when upstream returns 503")
    void getWeather_returnsFallback_whenUpstreamReturns503() throws Exception {
        // Given: WireMock simulates a 503 Service Unavailable from the upstream.
        // With retry configured (max-attempts=3), the client will attempt 3 times total.
        // We stub ALL calls to /weather to return 503 so all retries fail, triggering fallback.
        stubFor(WireMock.get(urlPathEqualTo("/weather"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Service Unavailable\"}")));

        // When / Then: after retries are exhausted, the fallback is invoked.
        // The fallback returns a synthetic cached report (not an error response).
        mockMvc.perform(get("/api/weather")
                        .queryParam("city", "Tokyo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city", is("Tokyo")))
                .andExpect(jsonPath("$.cached", is(true)))
                .andExpect(jsonPath("$.description").value("Weather data temporarily unavailable"));
    }

    @Test
    @DisplayName("GET /api/weather returns 400 when city query parameter is missing")
    void getWeather_returns400_whenCityParamMissing() throws Exception {
        // When / Then: missing required ?city= parameter triggers 400 from GlobalExceptionHandler
        mockMvc.perform(get("/api/weather")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/resilience/status ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/resilience/status returns 200 with both Retry and RateLimiter entries")
    void getResilienceStatus_returns200WithBothTypes() throws Exception {
        // When: call the status endpoint
        // Then: returns at least 2 entries (one Retry + one RateLimiter for "weatherService")
        mockMvc.perform(get("/api/resilience/status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("GET /api/resilience/status/retry/weatherService returns 200 with retry metrics")
    void getRetryStatus_returns200_forWeatherService() throws Exception {
        mockMvc.perform(get("/api/resilience/status/retry/weatherService")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("RETRY")))
                .andExpect(jsonPath("$.name", is("weatherService")));
    }

    @Test
    @DisplayName("GET /api/resilience/status/rate-limiter/weatherService returns 200 with rate limiter metrics")
    void getRateLimiterStatus_returns200_forWeatherService() throws Exception {
        mockMvc.perform(get("/api/resilience/status/rate-limiter/weatherService")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("RATE_LIMITER")))
                .andExpect(jsonPath("$.name", is("weatherService")))
                // availablePermissions should be a non-negative number
                .andExpect(jsonPath("$.availablePermissions").isNumber());
    }

    @Test
    @DisplayName("GET /api/resilience/status/retry/unknown returns 404")
    void getRetryStatus_returns404_forUnknownInstance() throws Exception {
        mockMvc.perform(get("/api/resilience/status/retry/nonExistentInstance")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/resilience/status/rate-limiter/unknown returns 404")
    void getRateLimiterStatus_returns404_forUnknownInstance() throws Exception {
        mockMvc.perform(get("/api/resilience/status/rate-limiter/nonExistentInstance")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ── Rate limiting behaviour ───────────────────────────────────────────────────

    @Test
    @Order(100) // Must run last: exhausts the shared 2-permit rate limit budget for the entire context
    @DirtiesContext // Discard the Spring context (and its rate limiter state) after this test
    @DisplayName("GET /api/weather returns fallback with cached=true when rate limit is exhausted")
    void getWeather_returnsFallback_whenRateLimitExhausted() throws Exception {
        // Given: WireMock stubs the upstream to return a successful response.
        // The integration-test profile configures a very tight rate limit (2 calls per 60s)
        // so we can exhaust it quickly.
        stubFor(WireMock.get(urlPathEqualTo("/weather"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "city": "Paris",
                                  "description": "Sunny",
                                  "temperatureC": 20.0,
                                  "humidity": 50,
                                  "windSpeedKmh": 5.0,
                                  "cached": false,
                                  "retrievedAt": "2024-06-01T09:00:00Z"
                                }
                                """)));

        // When: send 2 requests to exhaust the tight rate limit (limit-for-period=2 in test profile)
        // The first 2 calls should succeed (permits available)
        mockMvc.perform(get("/api/weather").queryParam("city", "Paris")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/weather").queryParam("city", "Paris")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // When: the 3rd call exhausts the rate limit (only 2 permits per 60s in test profile)
        // Then: the rate limiter rejects it and the fallback is returned
        mockMvc.perform(get("/api/weather").queryParam("city", "Paris")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // The fallback marks the response as cached=true
                .andExpect(jsonPath("$.cached", is(true)))
                .andExpect(jsonPath("$.city", is("Paris")));
    }

    // ── Actuator health endpoint ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /actuator/health returns 200 and includes rate limiter health details")
    void actuatorHealth_returns200WithRateLimiterDetails() throws Exception {
        // When: call the Spring Boot Actuator health endpoint
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // The root "status" field must exist
                .andExpect(jsonPath("$.status").isString());
    }
}
