package com.example.cloudstream.integration;

import com.example.cloudstream.domain.Order;
import com.example.cloudstream.domain.OrderStatus;
import com.example.cloudstream.repository.OrderRepository;
import com.example.cloudstream.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.TimeUnit;

/**
 * Full integration test for the Spring Cloud Stream order pipeline.
 *
 * <p>Uses Testcontainers to start a real Kafka broker in Docker.
 * Spring Cloud Stream is configured with the real Kafka binder (not the test binder),
 * so messages flow through an actual Kafka broker exactly as in production.
 *
 * <p>The {@link DynamicPropertySource} annotation injects the Testcontainers-assigned
 * Kafka bootstrap address into the Spring application context before it starts,
 * overriding the {@code spring.cloud.stream.kafka.binder.brokers} value in
 * {@code application.yml}.
 *
 * <p>We use <strong>Awaitility</strong> (bundled with Spring Boot Test) to poll
 * the in-memory order store until the expected status transition occurs. This is
 * necessary because the Spring Cloud Stream pipeline is asynchronous — the message
 * travels through Kafka before the consumer updates the order status.
 *
 * <p>Test coverage:
 * <ol>
 *   <li>POST /api/orders creates an order with PENDING status.</li>
 *   <li>Valid order flows through the full pipeline:
 *       PENDING → PROCESSING → NOTIFIED (via real Kafka).</li>
 *   <li>Invalid order (zero price) flows to rejection:
 *       PENDING → REJECTED (via real Kafka).</li>
 *   <li>GET /api/orders/{id} returns correct status after pipeline completes.</li>
 *   <li>GET /api/orders/{id} returns 404 for unknown UUID.</li>
 *   <li>POST /api/orders returns 400 for invalid request body.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Order Pipeline Integration Tests (real Kafka via Testcontainers)")
class OrderPipelineIntegrationTest {

    // -------------------------------------------------------------------------
    // Testcontainers — real Kafka broker
    // -------------------------------------------------------------------------

    /**
     * Kafka container using the Confluent Platform image.
     * The {@code confluentinc/cp-kafka:7.6.1} image supports both AMD64 and ARM64
     * (Apple Silicon), making it suitable for CI/CD across different architectures.
     *
     * <p>Declared as {@code static} so Testcontainers shares a single container
     * across all test methods in this class (faster than restarting per test).
     */
    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /**
     * Injects the Testcontainers-assigned Kafka bootstrap address into the Spring
     * application context before it starts. This overrides the broker address
     * in {@code application.yml} with the dynamically assigned port.
     *
     * <p>Why {@code spring.cloud.stream.kafka.binder.brokers}?
     * This is the Spring Cloud Stream Kafka binder property for the broker address.
     * Setting it here ensures the binder connects to the Testcontainers Kafka
     * instance rather than the default {@code localhost:9092}.
     */
    @DynamicPropertySource
    static void overrideKafkaBrokers(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers",
                kafka::getBootstrapServers);
    }

    // -------------------------------------------------------------------------
    // Spring-injected components
    // -------------------------------------------------------------------------

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        // Clear in-memory state between tests to prevent cross-test contamination
        orderRepository.deleteAll();
    }

    // =========================================================================
    // REST API tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/orders returns 201 Created with order body and Location header")
    void postOrderReturns201() throws Exception {
        Map<String, Object> body = Map.of(
                "customerId", "cust-rest-test",
                "productId", "prod-1",
                "quantity", 2,
                "totalPrice", "19.98"
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("cust-rest-test"))
                .andExpect(jsonPath("$.productId").value("prod-1"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/orders returns 400 Bad Request when customerId is blank")
    void postOrderReturnsBadRequestForBlankCustomerId() throws Exception {
        Map<String, Object> body = Map.of(
                "customerId", "",
                "productId", "prod-1",
                "quantity", 1,
                "totalPrice", "10.00"
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders returns 400 Bad Request when quantity is negative")
    void postOrderReturnsBadRequestForNegativeQuantity() throws Exception {
        Map<String, Object> body = Map.of(
                "customerId", "cust-1",
                "productId", "prod-1",
                "quantity", -1,
                "totalPrice", "10.00"
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns 404 for unknown UUID")
    void getOrderReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns 200 with the order after creation")
    void getOrderReturnsOrderAfterCreation() throws Exception {
        // Place an order via the service (bypasses REST to keep this test focused)
        Order order = orderService.placeOrder("cust-get", "prod-get", 1,
                new BigDecimal("10.00"));

        mockMvc.perform(get("/api/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.customerId").value("cust-get"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders returns a list containing all placed orders")
    void listOrdersReturnsAllOrders() throws Exception {
        orderService.placeOrder("cust-list-1", "prod-L1", 1, new BigDecimal("5.00"));
        orderService.placeOrder("cust-list-2", "prod-L2", 2, new BigDecimal("10.00"));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // =========================================================================
    // End-to-end pipeline tests (real Kafka)
    // =========================================================================

    @Test
    @DisplayName("Valid order flows through the full pipeline: PENDING → PROCESSING → NOTIFIED")
    void validOrderFlowsThroughFullPipeline() {
        // Act: place a valid order
        Order order = orderService.placeOrder(
                "cust-pipeline", "prod-pipeline", 2, new BigDecimal("29.98"));

        UUID orderId = order.getId();

        // Assert: order starts as PENDING
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // The pipeline is asynchronous. Awaitility polls every 500ms for up to 30s.
        // First, wait for PROCESSING (after orderProcessor consumes from "orders")
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> found = orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus())
                            .isIn(OrderStatus.PROCESSING, OrderStatus.NOTIFIED);
                });

        // Then, wait for NOTIFIED (after notificationConsumer consumes from "orders-processed")
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> found = orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.NOTIFIED);
                });
    }

    @Test
    @DisplayName("Order with zero totalPrice is rejected: PENDING → REJECTED")
    void orderWithZeroPriceIsRejected() throws Exception {
        // POST an order where totalPrice will pass REST validation (positive)
        // but will fail the processor's domain validation (we test processor rejection
        // by directly placing an order via the service with a zero price, bypassing
        // the REST controller's @Positive validation)
        Order order = orderService.placeOrder(
                "cust-reject", "prod-reject", 1, new BigDecimal("0.00"));

        // We bypass the REST controller here because the REST layer already validates
        // @Positive, so we call the service directly with zero price to test the
        // processor's validation path.
        UUID orderId = order.getId();

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Optional<Order> found = orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.REJECTED);
                    assertThat(found.get().getRejectionReason())
                            .contains("Total price must be positive");
                });
    }
}
