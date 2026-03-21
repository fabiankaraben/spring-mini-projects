package com.example.eurekadiscoveryserver.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the Eureka Discovery Server.
 *
 * <p><b>What is being tested here?</b>
 * These tests start a complete Spring Boot application context (including the
 * embedded Eureka server) on a random port, then send real HTTP requests to
 * verify that:
 * <ul>
 *   <li>The Eureka server starts successfully and its core endpoints respond.</li>
 *   <li>The Eureka web dashboard (HTML UI) is served at the root path.</li>
 *   <li>The Eureka REST API endpoint for fetching all registered apps is live.</li>
 *   <li>The Spring Boot Actuator health endpoint reports the server as healthy.</li>
 *   <li>Our custom {@code /registry/summary} and {@code /registry/instances} endpoints
 *       respond correctly with valid JSON.</li>
 * </ul>
 *
 * <p><b>Why Testcontainers with {@code @Testcontainers}?</b>
 * Testcontainers is used here for its JUnit 5 lifecycle integration (the
 * {@code @Testcontainers} annotation). Even though this particular Eureka server
 * has no external Docker dependencies (no database, no broker), using
 * {@code @Testcontainers} demonstrates the pattern and allows the test class to
 * be extended later with containerised downstream services (e.g. a Eureka client
 * running inside a container registering with this server).
 *
 * <p><b>Key annotations explained:</b>
 * <ul>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} — starts the full
 *       application context with a real embedded servlet container on a random
 *       free port. The random port avoids port conflicts in CI environments.</li>
 *   <li>{@code @LocalServerPort} — injects the actual port chosen at runtime.</li>
 *   <li>{@code TestRestTemplate} — a test-friendly HTTP client that automatically
 *       handles relative URLs (when baseUrl is not set) and does not throw
 *       exceptions on 4xx/5xx responses (it returns them as ResponseEntity).</li>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Eureka Discovery Server — integration tests")
class EurekaServerIntegrationTest {

    /**
     * The random port assigned to the embedded servlet container by Spring Boot.
     * Injected at runtime after the application context is started.
     */
    @LocalServerPort
    private int port;

    /**
     * A test-friendly HTTP client provided by Spring Boot Test.
     * Unlike a regular {@link org.springframework.web.client.RestTemplate},
     * {@link TestRestTemplate} does NOT throw exceptions on HTTP error responses
     * — it returns them as {@link ResponseEntity} objects so assertions can
     * inspect the status code and body.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    // =========================================================================
    // Eureka built-in endpoints
    // =========================================================================

    /**
     * Verifies that the Eureka web dashboard is served at the root path.
     *
     * <p>The dashboard is a Freemarker-rendered HTML page showing all registered
     * services. It is enabled by default and served by the Eureka server's own
     * web layer (not our custom controllers).
     */
    @Test
    @DisplayName("Eureka dashboard is accessible at /")
    void eurekaDashboardShouldBeAccessible() {
        // Act: GET / on the running server
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/", String.class);

        // Assert: the dashboard returns HTTP 200 and some HTML content
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
    }

    /**
     * Verifies that the Eureka REST API endpoint for fetching all registered
     * applications responds with HTTP 200.
     *
     * <p>This endpoint ({@code /eureka/apps}) is used by Eureka clients (other
     * microservices) to discover all registered services. It returns XML or JSON
     * depending on the Accept header.
     */
    @Test
    @DisplayName("Eureka /eureka/apps endpoint responds with 200")
    void eurekaAppsEndpointShouldRespond() {
        // Act: GET /eureka/apps — the standard Eureka discovery endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/eureka/apps", String.class);

        // Assert: endpoint is reachable and returns success
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // Spring Boot Actuator endpoints
    // =========================================================================

    /**
     * Verifies that the Spring Boot Actuator health endpoint reports the
     * application as UP.
     *
     * <p>The health endpoint aggregates health indicators from all registered
     * components. For a standalone Eureka server with no external dependencies
     * (no database, no broker), the overall status should always be UP.
     */
    @Test
    @DisplayName("Actuator /actuator/health reports status UP")
    void actuatorHealthShouldReportUp() {
        // Act: GET /actuator/health
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        // Assert: HTTP 200 and the body contains "UP"
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    // =========================================================================
    // Custom /registry endpoints
    // =========================================================================

    /**
     * Verifies that the custom {@code /registry/summary} endpoint responds
     * with HTTP 200 and returns a valid JSON body.
     *
     * <p>Since no other services have registered with this test server, the
     * summary should report all-zero counts — but the endpoint must still be
     * reachable and return valid JSON.
     */
    @Test
    @DisplayName("GET /registry/summary returns HTTP 200 with JSON body")
    void registrySummaryShouldReturn200() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/registry/summary", String.class);

        // Assert: endpoint responds successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Body should be a JSON object (starts with '{')
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody().trim()).startsWith("{");
    }

    /**
     * Verifies that the custom {@code /registry/summary} endpoint returns
     * all-zero counts when no services are registered.
     *
     * <p>In this test context, the Eureka server starts but no Eureka clients
     * register with it. The server does NOT register itself (register-with-eureka
     * is false in application.yml). Therefore, all counts should be zero.
     */
    @Test
    @DisplayName("GET /registry/summary reports zero counts when no services are registered")
    void registrySummaryShouldReportZeroWhenEmpty() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/registry/summary", String.class);

        // Assert: all counts are zero
        String body = response.getBody();
        assertThat(body).contains("\"totalApplications\":0");
        assertThat(body).contains("\"totalInstances\":0");
        assertThat(body).contains("\"healthyInstances\":0");
        assertThat(body).contains("\"unhealthyInstances\":0");
    }

    /**
     * Verifies that the custom {@code /registry/instances} endpoint responds
     * with HTTP 200 and returns a JSON array (empty when no services registered).
     */
    @Test
    @DisplayName("GET /registry/instances returns HTTP 200 with empty JSON array when no services registered")
    void registryInstancesShouldReturnEmptyArrayWhenNoServices() {
        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/registry/instances", String.class);

        // Assert: HTTP 200 and empty JSON array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().trim()).isEqualTo("[]");
    }

    /**
     * Verifies that querying instances for an unknown app name returns an
     * empty JSON array (not a 404 or error).
     */
    @Test
    @DisplayName("GET /registry/instances/{appName} returns empty array for unknown service")
    void registryInstancesByAppNameShouldReturnEmptyForUnknownService() {
        // Act: look up a service name that has never registered
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/registry/instances/UNKNOWN-SERVICE",
                String.class);

        // Assert: HTTP 200 and empty JSON array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().trim()).isEqualTo("[]");
    }
}
