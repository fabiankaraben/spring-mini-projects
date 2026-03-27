package com.example.camel.integration;

import com.example.camel.domain.OrderResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration test for the Order processing pipeline.
 *
 * <p>This test starts the complete Spring Boot application context — including the Camel context
 * with all routes and processors — and exercises it via the REST API using {@link MockMvc}.
 * A real ActiveMQ Artemis broker is spun up by Testcontainers so that the JMS dispatch stage
 * of the pipeline can actually deliver messages to a queue.
 *
 * <h3>Testcontainers setup</h3>
 * <p>We use a {@link GenericContainer} with the {@code apache/activemq-artemis} Docker image.
 * Artemis exposes:
 * <ul>
 *   <li>Port 61616 — OpenWire/AMQP/CORE protocol acceptor (used by Spring Artemis client)</li>
 *   <li>Port 8161  — Management web console (not used in tests)</li>
 * </ul>
 * The {@link DynamicPropertySource} method overrides {@code spring.artemis.broker-url} with the
 * mapped host+port from the container so the Spring context connects to the right broker.
 *
 * <h3>Test profile</h3>
 * <p>The {@code test} profile activates {@code application-test.yml} which:
 * <ul>
 *   <li>Points file output to {@code ./target/test-output/orders} (cleaned by {@code mvn clean})</li>
 *   <li>Reduces logging noise</li>
 * </ul>
 *
 * <h3>What these tests verify</h3>
 * <ol>
 *   <li>A valid standard order (below threshold) returns HTTP 202 and status ACCEPTED</li>
 *   <li>A valid priority order (above threshold) returns HTTP 202 and status ACCEPTED</li>
 *   <li>An invalid order (missing orderId) returns HTTP 422 and status REJECTED</li>
 *   <li>An order with negative price returns HTTP 422 and status REJECTED</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Order Pipeline — Integration Tests (Testcontainers)")
class OrderPipelineIntegrationTest {

    // ── Testcontainers — ActiveMQ Artemis broker ──────────────────────────────
    //
    // @Container makes Testcontainers manage the container lifecycle for all tests
    // in this class (shared container — started once, reused across all @Test methods).
    //
    // Image: apache/activemq-artemis:2.38.0 — official multi-arch image (AMD64 + ARM64).
    // Port 61616: the broker's CORE/AMQP/OpenWire acceptor used by the Spring client.
    // Port 8161:  the web management console (exposed but not used in tests).
    //
    // Wait strategy: poll the management HTTP endpoint until it returns HTTP 200.
    // This ensures the broker is fully initialised before Spring context starts.
    @Container
    static GenericContainer<?> artemisContainer = new GenericContainer<>("apache/activemq-artemis:2.38.0")
            .withExposedPorts(61616, 8161)
            .withEnv("ARTEMIS_USER", "artemis")
            .withEnv("ARTEMIS_PASSWORD", "artemis")
            // Wait until the management console responds — reliable signal that the broker is ready.
            .waitingFor(
                Wait.forHttp("/")
                    .forPort(8161)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(120))
            );

    /**
     * Dynamically overrides Spring properties after the Testcontainers container has started
     * but before the Spring application context is created.
     *
     * <p>This is the standard Testcontainers + Spring Boot pattern for injecting a
     * dynamic container port into Spring's environment.
     *
     * @param registry Spring's dynamic property registry — properties added here are resolved
     *                 during context startup like any other {@code application.yml} property.
     */
    @DynamicPropertySource
    static void artemisProperties(DynamicPropertyRegistry registry) {
        // Build the broker URL using the container's mapped host and port.
        // Testcontainers assigns a random free host port for each run to avoid collisions.
        String brokerUrl = "tcp://" + artemisContainer.getHost() + ":" +
                artemisContainer.getMappedPort(61616);

        registry.add("spring.artemis.broker-url", () -> brokerUrl);
        registry.add("spring.artemis.user", () -> "artemis");
        registry.add("spring.artemis.password", () -> "artemis");

        // Also update the app.broker.url property (used by AppProperties for reference).
        registry.add("app.broker.url", () -> brokerUrl);
    }

    // ── Test infrastructure ────────────────────────────────────────────────────

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Test cases ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid standard order (below threshold) → HTTP 202 ACCEPTED")
    void standardOrder_isAccepted() throws Exception {
        // A standard order: total = 9.99 × 2 = 19.98, which is below the 300 threshold.
        String payload = """
                {
                  "orderId": "ORD-IT-001",
                  "customerId": "EU-CUST-IT-1",
                  "productName": "Integration Widget",
                  "unitPrice": 9.99,
                  "quantity": 2
                }
                """;

        String responseBody = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())          // HTTP 202
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").value("ORD-IT-001"))
                .andReturn().getResponse().getContentAsString();

        OrderResult result = objectMapper.readValue(responseBody, OrderResult.class);
        assertThat(result.status()).isEqualTo("ACCEPTED");
        assertThat(result.orderId()).isEqualTo("ORD-IT-001");
    }

    @Test
    @DisplayName("Valid priority order (above threshold) → HTTP 202 ACCEPTED")
    void priorityOrder_isAccepted() throws Exception {
        // A priority order: total = 150.00 × 3 = 450.00, which exceeds the 300 threshold.
        String payload = """
                {
                  "orderId": "ORD-IT-002",
                  "customerId": "US-CUST-IT-2",
                  "productName": "Premium Gadget",
                  "unitPrice": 150.00,
                  "quantity": 3
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())          // HTTP 202
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").value("ORD-IT-002"));
    }

    @Test
    @DisplayName("Order with missing orderId → HTTP 422 REJECTED")
    void missingOrderId_isRejected() throws Exception {
        // The orderId field is absent — validation should reject this order.
        String payload = """
                {
                  "customerId": "EU-CUST-IT-3",
                  "productName": "Defective Widget",
                  "unitPrice": 5.00,
                  "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity()) // HTTP 422
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Order with negative unitPrice → HTTP 422 REJECTED")
    void negativeUnitPrice_isRejected() throws Exception {
        String payload = """
                {
                  "orderId": "ORD-IT-004",
                  "customerId": "EU-CUST-IT-4",
                  "productName": "Bad Price Widget",
                  "unitPrice": -1.00,
                  "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity()) // HTTP 422
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("Order with zero quantity → HTTP 422 REJECTED")
    void zeroQuantity_isRejected() throws Exception {
        String payload = """
                {
                  "orderId": "ORD-IT-005",
                  "customerId": "AP-CUST-IT-5",
                  "productName": "Zero Qty Widget",
                  "unitPrice": 10.00,
                  "quantity": 0
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity()) // HTTP 422
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("APAC region order is accepted and processed correctly")
    void apacRegionOrder_isAccepted() throws Exception {
        // customerId starting with "AP" → region APAC; total = 50.00 × 4 = 200 → STANDARD
        String payload = """
                {
                  "orderId": "ORD-IT-006",
                  "customerId": "AP-CUST-IT-6",
                  "productName": "APAC Widget",
                  "unitPrice": 50.00,
                  "quantity": 4
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").value("ORD-IT-006"));
    }
}
