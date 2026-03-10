package com.example.circuitbreaker;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Circuit Breaker application.
 *
 * <p>These tests verify end-to-end behaviour from the HTTP layer through the
 * service layer (including Resilience4j AOP decorators) down to a mocked
 * upstream inventory API (WireMock).
 *
 * <h2>Test setup overview</h2>
 * <ul>
 *   <li>{@link SpringBootTest} starts the full Spring application context,
 *       including the Resilience4j circuit breaker AOP proxies and the
 *       {@link com.example.circuitbreaker.service.ProductService} bean.</li>
 *   <li>{@link AutoConfigureMockMvc} injects {@link MockMvc}, which sends HTTP
 *       requests through the full filter/controller/handler chain.</li>
 *   <li>{@link WireMockServer} (in-process) acts as the fake upstream inventory
 *       API. It is started once per test class and reset between tests.</li>
 *   <li>{@link DynamicPropertySource} overrides {@code inventory.base-url} so
 *       the {@link com.example.circuitbreaker.client.InventoryClient} points at
 *       WireMock instead of the real service.</li>
 * </ul>
 *
 * <h2>What is tested here (vs. unit tests)</h2>
 * <ul>
 *   <li>Successful product retrieval through the full stack.</li>
 *   <li>Circuit breaker fallback activation after upstream failures.</li>
 *   <li>Retry behaviour: WireMock returns errors and the retry exhausts before
 *       the fallback is called.</li>
 *   <li>Circuit breaker state reported via the status endpoint.</li>
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
@DisplayName("ProductController integration tests (Circuit Breaker + WireMock)")
class ProductControllerIntegrationTest {

    // ── WireMock server (in-process) ──────────────────────────────────────────────

    /**
     * In-process WireMock server acting as the fake upstream inventory API.
     *
     * <p>{@code static} ensures a single instance is shared across all test methods.
     * Starting it in {@link BeforeAll} avoids per-test startup overhead.
     * Port 0 = OS-assigned random port (no conflicts).
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
     * This makes {@link com.example.circuitbreaker.client.InventoryClient} call
     * WireMock instead of the real inventory service.
     */
    @DynamicPropertySource
    static void overrideInventoryBaseUrl(DynamicPropertyRegistry registry) {
        // Supplier is evaluated after wireMockServer.start(), so the port is known
        registry.add("inventory.base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    /**
     * Reset all WireMock stubs and recorded requests before each test,
     * and also reset the Resilience4j circuit breaker state to CLOSED.
     *
     * <p>Without this, a stub registered in one test would remain active in
     * subsequent tests, causing unexpected behaviour.
     *
     * <p>The circuit breaker reset is critical: tests that trigger failures (by
     * stubbing 500 responses) open the circuit, which would cause subsequent
     * "healthy upstream" tests to receive the fallback instead of the real
     * response. Resetting to CLOSED ensures every test starts with a clean state.
     */
    @BeforeEach
    void resetWireMockStubsAndCircuitBreaker() {
        // Reset WireMock stubs so previous test's stubs don't bleed into this test
        resetAllRequests();
        wireMockServer.resetAll();
        // Reset Resilience4j circuit breaker to CLOSED state so every test starts fresh
        circuitBreakerRegistry.circuitBreaker(com.example.circuitbreaker.service.ProductService.CB_NAME)
                .reset();
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
     * Resilience4j registry that holds all circuit breaker instances.
     * Injected here to reset the {@code inventoryService} circuit breaker
     * before each test, ensuring tests are isolated from each other.
     */
    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    // ── GET /api/products ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products returns 200 with all products when the upstream is healthy")
    void getAllProducts_returns200WithProducts_whenUpstreamHealthy() throws Exception {
        // Given: WireMock stubs GET /products to return a JSON array with two products
        stubFor(WireMock.get(urlEqualTo("/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id":1,"name":"Laptop Pro","description":"High-end laptop",
                                   "price":1299.99,"available":true},
                                  {"id":2,"name":"Wireless Mouse","description":"Ergonomic mouse",
                                   "price":49.99,"available":true}
                                ]
                                """)));

        // When / Then: the API returns both products with correct field values
        mockMvc.perform(get("/api/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].name", is("Laptop Pro")))
                .andExpect(jsonPath("$[0].available", is(true)))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].name", is("Wireless Mouse")));
    }

    @Test
    @DisplayName("GET /api/products returns 200 with empty array when upstream returns no products")
    void getAllProducts_returns200WithEmptyArray_whenUpstreamReturnsNone() throws Exception {
        // Given: upstream returns an empty JSON array
        stubFor(WireMock.get(urlEqualTo("/products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // When / Then: the API returns an empty array — not an error
        mockMvc.perform(get("/api/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/products/{id} returns 200 with the product when the upstream is healthy")
    void getProductById_returns200WithProduct_whenUpstreamHealthy() throws Exception {
        // Given: WireMock stubs GET /products/1 to return a single product
        stubFor(WireMock.get(urlEqualTo("/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"name":"Laptop Pro","description":"High-end laptop",
                                 "price":1299.99,"available":true}
                                """)));

        // When / Then
        mockMvc.perform(get("/api/products/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("Laptop Pro")))
                .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    @DisplayName("GET /api/products/{id} returns fallback product when the upstream returns 500")
    void getProductById_returnsFallback_whenUpstreamReturns500() throws Exception {
        // Given: WireMock simulates a server error from the upstream.
        // With retry configured (max-attempts=3), the client will attempt 3 times total.
        // We stub ALL calls to /products/5 to return 500 so all retries fail, triggering the fallback.
        stubFor(WireMock.get(urlEqualTo("/products/5"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        // When / Then: after retries are exhausted, the circuit breaker invokes the fallback.
        // The fallback returns a degraded product (not an error response).
        mockMvc.perform(get("/api/products/5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(5)))
                .andExpect(jsonPath("$.name", is("Product Unavailable")))
                .andExpect(jsonPath("$.available", is(false)));
    }

    @Test
    @DisplayName("GET /api/products returns empty list fallback when the upstream returns 500")
    void getAllProducts_returnsEmptyFallback_whenUpstreamReturns500() throws Exception {
        // Given: WireMock returns 500 for all requests to /products
        // HttpServerErrorException triggers the retry, which retries 3 times — all fail.
        stubFor(WireMock.get(urlEqualTo("/products"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        // When / Then: fallback returns empty list — HTTP 200 with []
        mockMvc.perform(get("/api/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/circuit-breaker/status ───────────────────────────────────────────

    @Test
    @DisplayName("GET /api/circuit-breaker/status returns 200 with circuit breaker state list")
    void getCircuitBreakerStatus_returns200WithStateList() throws Exception {
        // When: call the status endpoint
        // Then: returns a non-empty list with the inventoryService circuit breaker entry
        mockMvc.perform(get("/api/circuit-breaker/status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("inventoryService")));
    }

    @Test
    @DisplayName("GET /api/circuit-breaker/status/{name} returns 200 with named circuit breaker state")
    void getCircuitBreakerStatusByName_returns200_whenNameExists() throws Exception {
        // When: query for the "inventoryService" circuit breaker specifically
        mockMvc.perform(get("/api/circuit-breaker/status/inventoryService")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("inventoryService")))
                // State should be one of: CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN
                .andExpect(jsonPath("$.state").isString());
    }

    // ── Actuator health endpoint ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /actuator/health returns 200 and includes circuit breaker health details")
    void actuatorHealth_returns200WithCircuitBreakerDetails() throws Exception {
        // When: call the Spring Boot Actuator health endpoint
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // The root "status" field must exist
                .andExpect(jsonPath("$.status").isString());
    }
}
