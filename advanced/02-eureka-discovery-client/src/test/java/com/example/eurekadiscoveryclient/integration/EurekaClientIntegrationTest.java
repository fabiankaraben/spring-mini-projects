package com.example.eurekadiscoveryclient.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Eureka Discovery Client.
 *
 * <p><b>What is being tested here?</b>
 * These tests start:
 * <ol>
 *   <li>A real Eureka Discovery Server inside a Docker container
 *       (managed by Testcontainers).</li>
 *   <li>The full Spring Boot application context for this Eureka Discovery
 *       Client, pointed at the containerised Eureka server.</li>
 * </ol>
 * They then send real HTTP requests to verify that:
 * <ul>
 *   <li>The application starts successfully and all REST endpoints respond.</li>
 *   <li>The Spring Boot Actuator health endpoint reports the application as UP.</li>
 *   <li>The custom {@code /client/status} endpoint returns a valid JSON response.</li>
 *   <li>The {@code /client/services} endpoint returns a JSON array.</li>
 *   <li>The product catalogue endpoints ({@code /products}) work correctly.</li>
 * </ul>
 *
 * <p><b>Why Testcontainers?</b>
 * The Eureka client requires a running Eureka server to register with on startup
 * (when registration is enabled). Testcontainers lets us spin up the official
 * Eureka server Docker image automatically during test execution — no manual
 * setup needed. The container is started once per test class and shared across
 * all test methods.
 *
 * <p><b>Key annotations explained:</b>
 * <ul>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} — starts the full
 *       application context with a real embedded servlet container on a random
 *       free port. The random port avoids port conflicts in CI environments.</li>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension,
 *       which manages the Docker container lifecycle.</li>
 *   <li>{@code @Container} — marks the Eureka server container field so
 *       Testcontainers starts it before the Spring context is created and stops
 *       it after all tests in the class have completed.</li>
 *   <li>{@code @DynamicPropertySource} — overrides Spring properties at runtime
 *       with values from the started container (e.g. the dynamically assigned
 *       port). This is the standard way to inject Testcontainers URLs into the
 *       Spring context.</li>
 * </ul>
 *
 * <p><b>Integration test configuration:</b>
 * The test overrides {@code eureka.client.register-with-eureka=false} and
 * {@code eureka.client.fetch-registry=false} to keep tests fast and isolated —
 * we are testing that our HTTP endpoints respond correctly, not the full
 * registration handshake. The {@code /client/status} endpoint will therefore
 * report {@code registeredServices: 0} and both flags as {@code false}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Eureka Discovery Client — integration tests")
class EurekaClientIntegrationTest {

    /**
     * Eureka Discovery Server running inside Docker, managed by Testcontainers.
     *
     * <p>We use the same Docker image that the Eureka Discovery Server mini-project
     * builds. However, since that image may not always be locally available in a
     * fresh checkout or CI, we use the widely available
     * {@code steeltoeoss/eureka-server} image which is a standard Eureka server
     * container used for testing Spring Cloud applications.
     *
     * <p>The container is declared {@code static} so Testcontainers starts it
     * once per test class (not once per test method), making the test suite
     * faster.
     *
     * <p>We wait for the Eureka server to be ready by polling its
     * {@code /actuator/health} endpoint. The {@code Wait.forHttp()} strategy
     * will retry the probe until the server responds with HTTP 200, or until
     * the startup timeout (90 s) is exceeded.
     */
    @Container
    static final GenericContainer<?> eurekaServer =
            new GenericContainer<>("steeltoeoss/eureka-server:latest")
                    .withExposedPorts(8761)
                    .waitingFor(
                            Wait.forHttp("/eureka/apps")
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofSeconds(90))
                    );

    /**
     * Overrides the Eureka client configuration at runtime with the URL of the
     * Testcontainers-managed Eureka server.
     *
     * <p>{@code @DynamicPropertySource} is called after the container is started
     * (so its mapped port is known) but before the Spring application context is
     * created. This is the recommended pattern for injecting Testcontainers
     * connection details into Spring tests.
     *
     * <p>We keep registration and registry fetching DISABLED in integration tests
     * because:
     * <ul>
     *   <li>Registration requires the client to send its own reachable URL to
     *       the server. Inside a test JVM the URL may not be reachable from the
     *       Docker container.</li>
     *   <li>We are testing our HTTP API endpoints, not the Eureka heartbeat
     *       protocol.</li>
     * </ul>
     *
     * @param registry Spring's dynamic property registry (injected by the framework)
     */
    @DynamicPropertySource
    static void eurekaProperties(DynamicPropertyRegistry registry) {
        // Point the client at the containerised Eureka server
        String eurekaUrl = "http://localhost:" + eurekaServer.getMappedPort(8761) + "/eureka/";
        registry.add("eureka.client.service-url.defaultZone", () -> eurekaUrl);

        // Keep registration and fetch disabled for fast, isolated integration tests
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }

    /**
     * The random port assigned to the embedded servlet container by Spring Boot.
     * Injected at runtime after the application context is started.
     */
    @LocalServerPort
    private int port;

    /**
     * A test-friendly HTTP client provided by Spring Boot Test.
     * Does NOT throw on 4xx/5xx — returns them as {@link ResponseEntity}.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    // =========================================================================
    // Spring Boot Actuator endpoints
    // =========================================================================

    /**
     * Verifies that the Spring Boot Actuator health endpoint reports UP.
     *
     * <p>The health endpoint aggregates health indicators. With Eureka client
     * registration disabled, the only indicator is the default disk-space and
     * ping checks, which should always report UP.
     */
    @Test
    @DisplayName("Actuator /actuator/health reports status UP")
    void actuatorHealthShouldReportUp() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        // Assert: HTTP 200 and the body contains "UP"
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    // =========================================================================
    // Discovery client status endpoint
    // =========================================================================

    /**
     * Verifies that the {@code /client/status} endpoint responds with HTTP 200
     * and returns a valid JSON body.
     */
    @Test
    @DisplayName("GET /client/status returns HTTP 200 with JSON body")
    void clientStatusShouldReturn200() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/client/status", String.class);

        // Assert: HTTP 200 and a JSON object
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody().trim()).startsWith("{");
    }

    /**
     * Verifies that the {@code /client/status} endpoint returns the correct
     * application name from {@code spring.application.name}.
     */
    @Test
    @DisplayName("GET /client/status returns correct application name")
    void clientStatusShouldContainApplicationName() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/client/status", String.class);

        // Assert: body contains the application name
        assertThat(response.getBody()).contains("\"applicationName\"");
        assertThat(response.getBody()).contains("product-service");
    }

    /**
     * Verifies that with registration and fetch disabled, the status reflects
     * those settings (both flags false, zero discovered services).
     */
    @Test
    @DisplayName("GET /client/status reports correct flags when registration is disabled")
    void clientStatusShouldReflectDisabledRegistration() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/client/status", String.class);

        // Assert: both flags are false (as set via @DynamicPropertySource)
        assertThat(response.getBody()).contains("\"registrationEnabled\":false");
        assertThat(response.getBody()).contains("\"fetchEnabled\":false");
        // No services discovered since fetch is disabled
        assertThat(response.getBody()).contains("\"registeredServices\":0");
    }

    // =========================================================================
    // Discovery client services endpoints
    // =========================================================================

    /**
     * Verifies that the {@code /client/services} endpoint responds with
     * HTTP 200 and returns a JSON array.
     *
     * <p>With fetch disabled, the local registry cache is empty, so the
     * array will be empty — but the endpoint must still respond correctly.
     */
    @Test
    @DisplayName("GET /client/services returns HTTP 200 with JSON array")
    void clientServicesShouldReturnJsonArray() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/client/services", String.class);

        // Assert: HTTP 200 and a JSON array (empty when fetch is disabled)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().trim()).isEqualTo("[]");
    }

    /**
     * Verifies that the {@code /client/services/{serviceId}} endpoint responds
     * with HTTP 200 and an empty array for an unknown service ID.
     */
    @Test
    @DisplayName("GET /client/services/{serviceId} returns empty array for unknown service")
    void clientServicesByIdShouldReturnEmptyForUnknownService() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/client/services/UNKNOWN-SERVICE", String.class);

        // Assert: HTTP 200 and empty array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().trim()).isEqualTo("[]");
    }

    // =========================================================================
    // Product catalogue endpoints
    // =========================================================================

    /**
     * Verifies that the product catalogue endpoint returns HTTP 200 with a
     * non-empty JSON array containing the expected products.
     */
    @Test
    @DisplayName("GET /products returns HTTP 200 with product list")
    void productsShouldReturnList() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/products", String.class);

        // Assert: HTTP 200, a JSON array, and some expected product names
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().trim()).startsWith("[");
        assertThat(response.getBody()).contains("Laptop");
        assertThat(response.getBody()).contains("Mouse");
    }

    /**
     * Verifies that fetching a single product by ID returns the correct product.
     */
    @Test
    @DisplayName("GET /products/1 returns the first product")
    void productByIdShouldReturnCorrectProduct() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/products/1", String.class);

        // Assert: HTTP 200 and the product with id=1 is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"id\":1");
        assertThat(response.getBody()).contains("Laptop");
    }

    /**
     * Verifies that fetching a product with a non-existent ID returns HTTP 404.
     */
    @Test
    @DisplayName("GET /products/999 returns HTTP 404 for non-existent product")
    void productByIdShouldReturn404ForUnknownId() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/products/999", String.class);

        // Assert: HTTP 404 — product not found
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Verifies that all 5 sample products are present in the product list.
     */
    @Test
    @DisplayName("GET /products returns all 5 sample products")
    void productsShouldReturnAllFiveSampleProducts() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/products", String.class);

        // Assert: all expected product names are in the response
        String body = response.getBody();
        assertThat(body).contains("Laptop");
        assertThat(body).contains("Mouse");
        assertThat(body).contains("Monitor");
        assertThat(body).contains("Keyboard");
        assertThat(body).contains("USB-C Hub");
    }
}
