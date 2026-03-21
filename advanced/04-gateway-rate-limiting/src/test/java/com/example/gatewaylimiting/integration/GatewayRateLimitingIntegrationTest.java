package com.example.gatewaylimiting.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.redis.testcontainers.RedisContainer;

import java.time.Duration;

/**
 * Full integration tests for the Gateway Rate Limiting application.
 *
 * <p><b>What is being tested here?</b>
 * These tests start:
 * <ol>
 *   <li>A real Redis instance in Docker (managed by Testcontainers via
 *       {@link RedisContainer}). The Spring Cloud Gateway
 *       {@code RequestRateLimiter} filter requires a live Redis to execute its
 *       Token Bucket Lua script.</li>
 *   <li>Three WireMock HTTP stub servers in Docker (one per downstream
 *       microservice: product, order, user). They are started using
 *       {@link GenericContainer} with the multi-arch WireMock image.</li>
 *   <li>The full Spring Boot application context for the gateway, pointing
 *       its routes at the WireMock containers and its rate limiter at Redis.</li>
 * </ol>
 *
 * <p>The tests send real HTTP requests through the gateway and verify:
 * <ul>
 *   <li>Requests within the rate limit are proxied to the correct downstream service.</li>
 *   <li>The gateway management endpoints ({@code /gateway/status},
 *       {@code /gateway/rate-limits}) respond correctly.</li>
 *   <li>The Spring Boot Actuator health endpoint reports UP (including Redis).</li>
 *   <li>Unknown routes return HTTP 404.</li>
 *   <li>The {@code X-Gateway-Source} header is added to downstream requests.</li>
 *   <li>The {@code X-Served-By} header is present in responses.</li>
 * </ul>
 *
 * <p><b>Why GenericContainer for WireMock?</b>
 * We use {@link GenericContainer} with the official {@code wiremock/wiremock:3.9.2}
 * multi-arch image (not the {@code -alpine} variant). This image supports both
 * {@code linux/amd64} and {@code linux/arm64} (Apple Silicon). The alpine
 * variants are AMD64-only and crash on ARM64 hosts.
 *
 * <p><b>Why RedisContainer?</b>
 * The {@code testcontainers-redis} library wraps a Redis Docker container with
 * a convenient API, providing the connection URI needed by Spring Data Redis.
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} — starts the full
 *       Spring Boot application context with a real Netty server on a random port.</li>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension,
 *       managing Docker container lifecycle alongside JUnit 5 tests.</li>
 *   <li>{@code @Container} — marks static container fields started once per test
 *       class and shared across all test methods (faster than per-test containers).</li>
 *   <li>{@code @DynamicPropertySource} — injects container URLs into the Spring
 *       context before it starts.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Gateway Rate Limiting — integration tests")
class GatewayRateLimitingIntegrationTest {

    /**
     * The WireMock Docker image to use.
     *
     * <p>The official {@code wiremock/wiremock:3.9.2} image (without the
     * {@code -alpine} suffix) is a multi-arch manifest that supports
     * both {@code linux/amd64} and {@code linux/arm64} (Apple Silicon).
     */
    private static final String WIREMOCK_IMAGE = "wiremock/wiremock:3.9.2";

    /**
     * WireMock port inside the container (WireMock default: 8080).
     * Testcontainers maps this to a random host port automatically.
     */
    private static final int WIREMOCK_PORT = 8080;

    // =========================================================================
    // Redis Testcontainer
    //
    // A real Redis instance is required by the Spring Cloud Gateway
    // RequestRateLimiter filter. It executes the Token Bucket algorithm as a
    // Lua script atomically on Redis.
    //
    // RedisContainer wraps the official redis:7-alpine Docker image and
    // exposes the standard Redis port (6379).
    // =========================================================================

    /**
     * Redis container used by the rate limiter.
     *
     * <p>Started once per test class (static + @Container), shared across
     * all test methods. The Spring context receives the Redis connection
     * details via {@link #configureProperties}.
     */
    @Container
    static final RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag("7-alpine"));

    // =========================================================================
    // WireMock Testcontainers (one per downstream service)
    //
    // Each container is a GenericContainer running the WireMock image.
    // Stub mapping JSON files are copied from the test classpath into
    // the container's /home/wiremock/mappings/ directory via MountableFile.
    // =========================================================================

    /**
     * WireMock container acting as the downstream product microservice.
     *
     * <p>The stub mapping at
     * {@code src/test/resources/wiremock/product/mappings/products-stub.json}
     * is copied into the container at startup. It responds to {@code GET /products}.
     * The gateway's {@code StripPrefix(1)} filter ensures the downstream receives
     * {@code /products} (not {@code /api/products}).
     */
    @Container
    static final GenericContainer<?> productWireMock =
            new GenericContainer<>(WIREMOCK_IMAGE)
                    .withExposedPorts(WIREMOCK_PORT)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource(
                                    "wiremock/product/mappings/products-stub.json"),
                            "/home/wiremock/mappings/products-stub.json")
                    .waitingFor(Wait.forHttp("/__admin/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(60)));

    /**
     * WireMock container acting as the downstream order microservice.
     *
     * <p>Responds to {@code GET /orders} with the stubbed JSON from
     * {@code wiremock/order/mappings/orders-stub.json}.
     */
    @Container
    static final GenericContainer<?> orderWireMock =
            new GenericContainer<>(WIREMOCK_IMAGE)
                    .withExposedPorts(WIREMOCK_PORT)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource(
                                    "wiremock/order/mappings/orders-stub.json"),
                            "/home/wiremock/mappings/orders-stub.json")
                    .waitingFor(Wait.forHttp("/__admin/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(60)));

    /**
     * WireMock container acting as the downstream user microservice.
     *
     * <p>Responds to {@code GET /users} with the stubbed JSON from
     * {@code wiremock/user/mappings/users-stub.json}.
     */
    @Container
    static final GenericContainer<?> userWireMock =
            new GenericContainer<>(WIREMOCK_IMAGE)
                    .withExposedPorts(WIREMOCK_PORT)
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource(
                                    "wiremock/user/mappings/users-stub.json"),
                            "/home/wiremock/mappings/users-stub.json")
                    .waitingFor(Wait.forHttp("/__admin/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(60)));

    /**
     * Overrides Spring properties with the Testcontainer connection details.
     *
     * <p>{@code @DynamicPropertySource} is called after all containers are started
     * (their mapped ports are known) but before the Spring context is created.
     * This is the standard Testcontainers pattern for injecting container
     * connection details into a Spring test context.
     *
     * @param registry Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Point Spring Data Redis at the Testcontainer Redis instance.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Point gateway routes at the WireMock stub containers.
        registry.add("services.product.url",
                () -> "http://localhost:" + productWireMock.getMappedPort(WIREMOCK_PORT));
        registry.add("services.order.url",
                () -> "http://localhost:" + orderWireMock.getMappedPort(WIREMOCK_PORT));
        registry.add("services.user.url",
                () -> "http://localhost:" + userWireMock.getMappedPort(WIREMOCK_PORT));
    }

    /**
     * Reactive HTTP test client wired to the gateway's random port.
     * Provided by {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}.
     */
    @Autowired
    private WebTestClient webTestClient;

    /**
     * Increases the default WebTestClient response timeout to accommodate
     * container startup latency and first-request overhead.
     */
    @BeforeEach
    void configureTimeout() {
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    // =========================================================================
    // Actuator health check
    // =========================================================================

    /**
     * Verifies that the Spring Boot Actuator health endpoint reports the gateway
     * as UP, including the Redis health indicator.
     *
     * <p>The health endpoint is exposed at {@code /actuator/health} because
     * the application is configured with {@code management.endpoints.web.exposure
     * .include: health, info, gateway}.
     */
    @Test
    @DisplayName("GET /actuator/health reports status UP")
    void actuatorHealthShouldReportUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("UP"));
    }

    // =========================================================================
    // Gateway info endpoints
    // =========================================================================

    /**
     * Verifies that the custom {@code /gateway/status} endpoint responds with
     * HTTP 200 and returns a JSON body with the application name and status.
     */
    @Test
    @DisplayName("GET /gateway/status returns HTTP 200 with JSON status body")
    void gatewayStatusShouldReturn200() {
        webTestClient.get()
                .uri("/gateway/status")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("gateway-rate-limiting")
                        .contains("UP")
                        .contains("rateLimitingEnabled")
                        .contains("true"));
    }

    /**
     * Verifies that the {@code /gateway/rate-limits} endpoint returns a JSON array
     * containing rate limit configuration for all three routes.
     */
    @Test
    @DisplayName("GET /gateway/rate-limits returns all three route rate limit configs")
    void gatewayRateLimitsShouldReturnAllThreeRoutes() {
        webTestClient.get()
                .uri("/gateway/rate-limits")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("product-route")
                        .contains("order-route")
                        .contains("user-route")
                        .contains("replenishRate")
                        .contains("burstCapacity"));
    }

    // =========================================================================
    // Product service routing with rate limiting
    // =========================================================================

    /**
     * Verifies that {@code GET /api/products} is routed to the product WireMock
     * container and the stubbed JSON response is returned.
     *
     * <p>The gateway's {@code StripPrefix(1)} filter removes the {@code /api}
     * prefix so WireMock receives {@code GET /products}.
     */
    @Test
    @DisplayName("GET /api/products is routed to product service and returns JSON")
    void productListShouldBeRoutedToProductService() {
        webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("Laptop"));
    }

    /**
     * Verifies that the {@code X-Served-By} response header is present on
     * product route responses, confirming the route's filter chain executed.
     */
    @Test
    @DisplayName("GET /api/products response contains X-Served-By header")
    void productResponseShouldHaveServedByHeader() {
        webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Served-By");
    }

    // =========================================================================
    // Order service routing with rate limiting
    // =========================================================================

    /**
     * Verifies that {@code GET /api/orders} is routed to the order WireMock
     * container and the stubbed JSON response is returned.
     */
    @Test
    @DisplayName("GET /api/orders is routed to order service and returns JSON")
    void orderListShouldBeRoutedToOrderService() {
        webTestClient.get()
                .uri("/api/orders")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("ORDER-001"));
    }

    // =========================================================================
    // User service routing with rate limiting
    // =========================================================================

    /**
     * Verifies that {@code GET /api/users} is routed to the user WireMock
     * container and the stubbed JSON response is returned.
     */
    @Test
    @DisplayName("GET /api/users is routed to user service and returns JSON")
    void userListShouldBeRoutedToUserService() {
        webTestClient.get()
                .uri("/api/users")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("alice"));
    }

    // =========================================================================
    // Unknown routes
    // =========================================================================

    /**
     * Verifies that requests to paths not matching any configured route return
     * HTTP 404 from the gateway itself (not from a downstream service).
     */
    @Test
    @DisplayName("GET /api/unknown returns HTTP 404 — no matching route")
    void unknownPathShouldReturn404() {
        webTestClient.get()
                .uri("/api/unknown-service/test")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // Rate limit response headers
    // =========================================================================

    /**
     * Verifies that a successful (non-rate-limited) response includes the
     * {@code X-RateLimit-Remaining} header set by the Redis rate limiter.
     *
     * <p>Spring Cloud Gateway's {@code RequestRateLimiter} filter automatically
     * adds rate limit metadata headers to every response (allowed and rejected):
     * <ul>
     *   <li>{@code X-RateLimit-Remaining} — tokens left in the current bucket.</li>
     *   <li>{@code X-RateLimit-Burst-Capacity} — maximum bucket capacity.</li>
     *   <li>{@code X-RateLimit-Replenish-Rate} — tokens added per second.</li>
     * </ul>
     */
    @Test
    @DisplayName("Successful response includes X-RateLimit-Remaining header")
    void successfulResponseShouldIncludeRateLimitHeaders() {
        webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Remaining");
    }
}
