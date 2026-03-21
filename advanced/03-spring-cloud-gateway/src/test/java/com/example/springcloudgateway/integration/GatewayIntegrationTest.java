package com.example.springcloudgateway.integration;

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

import java.time.Duration;

/**
 * Full integration tests for the Spring Cloud Gateway.
 *
 * <p><b>What is being tested here?</b>
 * These tests start:
 * <ol>
 *   <li>Three WireMock HTTP stub servers inside Docker containers (one per
 *       downstream microservice: product, order, user), managed by
 *       Testcontainers using {@link GenericContainer}.</li>
 *   <li>The full Spring Boot application context for the gateway, pointing
 *       its routes at those WireMock containers.</li>
 * </ol>
 * They then send real HTTP requests through the gateway and verify:
 * <ul>
 *   <li>Requests are correctly proxied to the appropriate downstream service.</li>
 *   <li>The {@code StripPrefix} filter removes the {@code /api} path prefix.</li>
 *   <li>The {@code RewritePath} filter rewrites the user service path.</li>
 *   <li>The gateway adds the {@code X-Correlation-ID} header to responses.</li>
 *   <li>The management endpoints ({@code /gateway/status}, {@code /gateway/routes})
 *       respond correctly.</li>
 *   <li>The Spring Boot Actuator health endpoint reports UP.</li>
 *   <li>Unknown routes return HTTP 404 (not routed).</li>
 * </ul>
 *
 * <p><b>Why WireMock in GenericContainer?</b>
 * WireMock is a mock HTTP server. We run it using Testcontainers'
 * {@link GenericContainer} with the official multi-arch
 * {@code wiremock/wiremock:3.9.2} image. This approach:
 * <ul>
 *   <li>Works on both AMD64 and ARM64 (Apple Silicon) hosts.</li>
 *   <li>Mounts stub JSON files from the test classpath into the container at
 *       the WireMock mappings directory using {@link MountableFile}.</li>
 *   <li>Starts real HTTP servers so the gateway routes real HTTP traffic.</li>
 * </ul>
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} — starts the full
 *       application context with a real Netty server on a random free port.</li>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5
 *       extension, managing Docker container lifecycle.</li>
 *   <li>{@code @Container} — marks fields whose containers are started before
 *       the Spring context and stopped after all tests complete.</li>
 *   <li>{@code @DynamicPropertySource} — injects WireMock container URLs into
 *       the Spring context before it starts.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Spring Cloud Gateway — integration tests")
class GatewayIntegrationTest {

    /**
     * The WireMock Docker image to use.
     *
     * <p>We use the official {@code wiremock/wiremock:3.9.2} image (without the
     * {@code -alpine} suffix) because it is a multi-arch manifest that supports
     * both {@code linux/amd64} and {@code linux/arm64} (Apple Silicon).
     * The {@code -alpine} variants are AMD64-only and will fail on ARM64 hosts.
     */
    private static final String WIREMOCK_IMAGE = "wiremock/wiremock:3.9.2";

    /**
     * WireMock port inside the container (WireMock default).
     * Testcontainers maps this to a random host port automatically.
     */
    private static final int WIREMOCK_PORT = 8080;

    // =========================================================================
    // WireMock Testcontainers (one per downstream service)
    //
    // Each container is a GenericContainer running the WireMock image.
    // Stub mapping JSON files are copied from the test classpath
    // (src/test/resources/wiremock/<service>/mappings/) into the container's
    // /home/wiremock/mappings/ directory using MountableFile.
    //
    // Static containers are started ONCE per test class and shared across all
    // test methods, making the test suite much faster than per-test containers.
    // =========================================================================

    /**
     * WireMock container acting as the downstream product microservice.
     *
     * <p>The stub mapping at
     * {@code src/test/resources/wiremock/product/mappings/products-stub.json}
     * is copied into the container at startup.
     * The gateway's StripPrefix(1) filter forwards {@code /products/**} to
     * this container (removing the leading {@code /api} segment).
     */
    @Container
    static final GenericContainer<?> productWireMock =
            new GenericContainer<>(WIREMOCK_IMAGE)
                    .withExposedPorts(WIREMOCK_PORT)
                    // Copy the products stub mapping file into the WireMock
                    // mappings directory inside the container before startup.
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource(
                                    "wiremock/product/mappings/products-stub.json"),
                            "/home/wiremock/mappings/products-stub.json")
                    // Wait until WireMock's admin health endpoint responds HTTP 200.
                    .waitingFor(Wait.forHttp("/__admin/health")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(60)));

    /**
     * WireMock container acting as the downstream order microservice.
     *
     * <p>Stub at {@code wiremock/order/mappings/orders-stub.json} is copied
     * in at startup.
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
     * <p>The user service route applies a {@code RewritePath} filter that
     * converts {@code /api/users/**} → {@code /v1/users/**}, so the stub
     * in {@code users-stub.json} maps {@code GET /v1/users} (not
     * {@code /api/users}).
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
     * Overrides the gateway's downstream service URLs with the dynamically
     * assigned host ports of the WireMock containers.
     *
     * <p>{@code @DynamicPropertySource} is called after containers are started
     * (so their mapped ports are known) but before the Spring application
     * context is created. This is the recommended pattern for injecting
     * Testcontainers connection details into Spring tests.
     *
     * <p>{@code getMappedPort(WIREMOCK_PORT)} returns the random host port that
     * Docker mapped to the container's port 8080. Using {@code localhost} works
     * because Testcontainers maps container ports to the host loopback address.
     *
     * @param registry Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void configureDownstreamUrls(DynamicPropertyRegistry registry) {
        // Build the base URL for each WireMock container using the randomly
        // assigned host port. The gateway will forward requests to these URLs.
        registry.add("services.product.url",
                () -> "http://localhost:" + productWireMock.getMappedPort(WIREMOCK_PORT));
        registry.add("services.order.url",
                () -> "http://localhost:" + orderWireMock.getMappedPort(WIREMOCK_PORT));
        registry.add("services.user.url",
                () -> "http://localhost:" + userWireMock.getMappedPort(WIREMOCK_PORT));
    }

    /**
     * Reactive HTTP test client provided by Spring Boot Test.
     * Wired to the gateway's random port automatically via
     * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}.
     */
    @Autowired
    private WebTestClient webTestClient;

    /**
     * Increase the default WebTestClient response timeout to accommodate
     * WireMock container startup and first-request latency.
     */
    @BeforeEach
    void configureTimeout() {
        webTestClient = webTestClient.mutate()
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    // =========================================================================
    // Actuator health check
    // =========================================================================

    /**
     * Verifies that the Spring Boot Actuator health endpoint reports the
     * gateway as UP. This confirms the application context started correctly.
     */
    @Test
    @DisplayName("GET /actuator/health reports status UP")
    void actuatorHealthShouldReportUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                // HTTP 200
                .expectStatus().isOk()
                // Body contains "UP"
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("UP"));
    }

    // =========================================================================
    // Gateway management endpoints
    // =========================================================================

    /**
     * Verifies that the custom {@code /gateway/status} endpoint responds with
     * HTTP 200 and returns a JSON body containing the application name and status.
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
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("\"applicationName\"")
                            .contains("spring-cloud-gateway")
                            .contains("\"status\"")
                            .contains("UP");
                });
    }

    /**
     * Verifies that the {@code /gateway/routes} endpoint returns HTTP 200
     * with a JSON array containing the three configured routes.
     */
    @Test
    @DisplayName("GET /gateway/routes returns HTTP 200 with all three routes")
    void gatewayRoutesShouldReturnAllThreeRoutes() {
        webTestClient.get()
                .uri("/gateway/routes")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("product-service-route")
                            .contains("order-service-route")
                            .contains("user-service-route");
                });
    }

    /**
     * Verifies that fetching a single route by ID returns HTTP 200 and the
     * correct route details.
     */
    @Test
    @DisplayName("GET /gateway/routes/product-service-route returns route details")
    void gatewayRouteByIdShouldReturnCorrectRoute() {
        webTestClient.get()
                .uri("/gateway/routes/product-service-route")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body ->
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("product-service-route")
                );
    }

    /**
     * Verifies that looking up a non-existent route ID returns HTTP 404.
     */
    @Test
    @DisplayName("GET /gateway/routes/non-existent-route returns HTTP 404")
    void gatewayRouteByUnknownIdShouldReturn404() {
        webTestClient.get()
                .uri("/gateway/routes/non-existent-route")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // Product service routing
    // =========================================================================

    /**
     * Verifies that {@code GET /api/products} is routed to the product
     * WireMock and the response is returned to the client.
     *
     * <p>The gateway strips the {@code /api} prefix (StripPrefix=1) so WireMock
     * receives {@code GET /products} and responds with the stubbed JSON.
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
                .value(body ->
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("Laptop")
                );
    }

    /**
     * Verifies that the gateway adds the {@code X-Correlation-ID} header to
     * responses from downstream services.
     *
     * <p>The {@link com.example.springcloudgateway.filter.RequestIdGlobalFilter}
     * is responsible for generating and propagating this header.
     */
    @Test
    @DisplayName("GET /api/products response contains X-Correlation-ID header")
    void productRequestShouldHaveCorrelationIdInResponse() {
        webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-ID");
    }

    /**
     * Verifies that a pre-set {@code X-Correlation-ID} header on the incoming
     * request is preserved (reused) in the response.
     */
    @Test
    @DisplayName("Existing X-Correlation-ID is preserved in the response")
    void existingCorrelationIdShouldBePreservedInResponse() {
        String clientCorrelationId = "client-provided-correlation-id-abc123";

        webTestClient.get()
                .uri("/api/products")
                .header("X-Correlation-ID", clientCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-ID", clientCorrelationId);
    }

    // =========================================================================
    // Order service routing
    // =========================================================================

    /**
     * Verifies that {@code GET /api/orders} is routed to the order WireMock
     * and returns the stubbed JSON response.
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
                .value(body ->
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("ORDER-001")
                );
    }

    // =========================================================================
    // User service routing (with RewritePath)
    // =========================================================================

    /**
     * Verifies that {@code GET /api/users} is routed to the user WireMock.
     * The RewritePath filter converts {@code /api/users/**} to
     * {@code /v1/users/**} before forwarding to the downstream service.
     *
     * <p>WireMock is configured to respond to {@code GET /v1/users} to match
     * what the gateway sends after the rewrite.
     */
    @Test
    @DisplayName("GET /api/users is routed to user service via RewritePath")
    void userListShouldBeRoutedToUserServiceWithRewrittenPath() {
        webTestClient.get()
                .uri("/api/users")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class)
                .value(body ->
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("alice")
                );
    }

    // =========================================================================
    // Unknown routes
    // =========================================================================

    /**
     * Verifies that requests to paths that don't match any configured route
     * return HTTP 404 from the gateway itself (not from a downstream service).
     */
    @Test
    @DisplayName("GET /api/unknown returns HTTP 404 — no matching route")
    void unknownPathShouldReturn404() {
        webTestClient.get()
                .uri("/api/unknown-service/test")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }
}
