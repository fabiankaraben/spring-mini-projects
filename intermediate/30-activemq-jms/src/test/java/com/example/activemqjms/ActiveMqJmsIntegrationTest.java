package com.example.activemqjms;

import com.example.activemqjms.domain.OrderMessage;
import com.example.activemqjms.dto.OrderRequest;
import com.example.activemqjms.service.MessageProducerService;
import com.example.activemqjms.service.OrderProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the ActiveMQ JMS application.
 *
 * <p>This test class verifies the end-to-end behaviour: from the HTTP layer
 * through the JMS producer, down to a real ActiveMQ broker (in Docker), and
 * back via the JMS consumer to the domain processing layer.
 *
 * <h2>Key aspects</h2>
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full Spring MVC filter chain,
 *       serialisation stack, and exception handlers are active — identical to
 *       production runtime.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up an ActiveMQ Classic
 *       Docker container for the duration of the test class. The container is
 *       <em>shared</em> across all test methods ({@code static} field) to avoid
 *       the overhead of restarting the broker for each test.</li>
 *   <li>{@link DynamicPropertySource} overrides the ActiveMQ broker URL in the
 *       Spring {@link org.springframework.core.env.Environment} before the application
 *       context is created. Testcontainers assigns a random host port, so we use
 *       the container's getter to discover it at runtime.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise during test runs.</li>
 * </ul>
 *
 * <h2>ActiveMQ Classic Docker image</h2>
 * <p>We use {@code apache/activemq-classic:5.18.3} — the official Apache image.
 * ActiveMQ Classic listens on port {@code 61616} for OpenWire (TCP) connections,
 * which is what Spring's {@code ActiveMQConnectionFactory} uses.
 *
 * <h2>End-to-end flow verified by these tests</h2>
 * <pre>
 *   TestRestTemplate
 *       │  POST /api/messages/orders
 *       ▼
 *   [MessageController]  ← Spring MVC (RANDOM_PORT)
 *       │
 *       ▼
 *   [MessageProducerService]
 *       │  JmsTemplate.convertAndSend(queue, message)
 *       ▼
 *   [ActiveMQ Classic]  ← Docker container (Testcontainers)
 *       │
 *       ▼
 *   [MessageConsumerService @JmsListener]
 *       │
 *       ▼
 *   [OrderProcessingService]  ← in-memory list
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("ActiveMQ JMS integration tests")
class ActiveMqJmsIntegrationTest {

    // ── Testcontainers ActiveMQ container ─────────────────────────────────────────

    /** ActiveMQ Classic OpenWire TCP port */
    private static final int ACTIVEMQ_PORT = 61616;

    /**
     * An ActiveMQ Classic container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single
     * container instance for the entire test class lifecycle, avoiding the
     * significant overhead of starting/stopping ActiveMQ for each test method.
     * The container is stopped automatically after the last test finishes.
     *
     * <p>We wait for the container to log "activemq.apache.org" which appears
     * in the ActiveMQ startup banner, indicating the broker is ready to accept
     * connections. A {@code startup_timeout} of 90 seconds is generous to
     * accommodate slow CI environments or first-time Docker image pulls.
     */
    @Container
    static final GenericContainer<?> ACTIVEMQ =
            new GenericContainer<>(DockerImageName.parse("apache/activemq-classic:5.18.3"))
                    .withExposedPorts(ACTIVEMQ_PORT)
                    .waitingFor(Wait.forLogMessage(".*activemq\\.apache\\.org.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(90)));

    /**
     * Register the container's dynamic broker URL into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>Testcontainers assigns random host ports to the container, so we use
     * {@link GenericContainer#getMappedPort} to discover them at runtime. This
     * property overrides the value in {@code application-test.yml}.
     *
     * @param registry the property registry that Spring Boot reads before context startup
     */
    @DynamicPropertySource
    static void activeMqProperties(DynamicPropertyRegistry registry) {
        // Build the broker URL using the container's dynamically assigned host port
        registry.add("spring.activemq.broker-url",
                () -> "tcp://" + ACTIVEMQ.getHost() + ":" + ACTIVEMQ.getMappedPort(ACTIVEMQ_PORT));
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /** The random port chosen by Spring Boot for the embedded servlet container. */
    @LocalServerPort
    int port;

    /**
     * {@link TestRestTemplate} is pre-configured to connect to the embedded
     * test server. It does NOT throw exceptions on 4xx/5xx responses, making
     * it ideal for asserting error scenarios.
     */
    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Direct access to the producer service for verifying behaviour at a layer
     * below the HTTP interface.
     */
    @Autowired
    MessageProducerService messageProducerService;

    /**
     * Direct access to the domain processing service to inspect which messages
     * have been consumed and processed by the JMS listener.
     */
    @Autowired
    OrderProcessingService orderProcessingService;

    /**
     * {@link JmsTemplate} used in tests to send raw messages directly to the
     * queue, bypassing the HTTP layer, for lower-level consumer verification.
     */
    @Autowired
    JmsTemplate jmsTemplate;

    /** Queue name injected from {@code application-test.yml}. */
    @Value("${app.jms.orders-queue}")
    String ordersQueue;

    /**
     * Wait for the async JMS consumer to finish processing before asserting.
     *
     * <p>The {@link com.example.activemqjms.service.MessageConsumerService}
     * runs on a JMS listener thread, which is separate from the test thread.
     * After publishing a message, we poll {@link OrderProcessingService#getProcessedOrderCount}
     * with a timeout to give the listener thread time to process the message.
     *
     * @param expectedCount the number of processed orders to wait for
     * @param timeoutMs     maximum time to wait in milliseconds
     * @throws InterruptedException if the waiting thread is interrupted
     */
    private void waitForProcessing(int expectedCount, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (orderProcessingService.getProcessedOrderCount() >= expectedCount) {
                return; // Consumer has processed the expected number of orders
            }
            Thread.sleep(100); // Poll every 100 ms
        }
        // Timeout reached — the test assertion will fail with a descriptive message
    }

    /**
     * Track the number of orders already processed before each test so we can
     * compute relative increments instead of relying on absolute counts.
     * This prevents test-order contamination when the shared container is used.
     */
    private int countBefore;

    @BeforeEach
    void recordCountBefore() {
        // Snapshot the current count so each test can measure its own relative change
        countBefore = orderProcessingService.getProcessedOrderCount();
    }

    // ── POST /api/messages/orders ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/messages/orders returns 202 Accepted with the published message body")
    void publishOrder_returns202WithOrderMessage() {
        // Given: a valid order request body
        OrderRequest request = new OrderRequest("ORD-INT-001", "Integration Laptop", 2);

        // When: POST the order via HTTP
        ResponseEntity<OrderMessage> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/messages/orders",
                request,
                OrderMessage.class
        );

        // Then: 202 Accepted is returned
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // And: the response body contains the published message with all required fields
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isEqualTo("ORD-INT-001");
        assertThat(response.getBody().getProduct()).isEqualTo("Integration Laptop");
        assertThat(response.getBody().getQuantity()).isEqualTo(2);
        assertThat(response.getBody().getMessageId()).isNotNull().isNotBlank();
        assertThat(response.getBody().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/messages/orders returns 400 when orderId is blank")
    void publishOrder_returns400_whenOrderIdIsBlank() {
        // Given: an invalid request with a blank orderId
        OrderRequest invalid = new OrderRequest("", "Product", 1);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/messages/orders",
                invalid,
                String.class
        );

        // Then: Bean Validation rejects the request before it reaches the service
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/messages/orders returns 400 when product is blank")
    void publishOrder_returns400_whenProductIsBlank() {
        // Given: an invalid request with a blank product name
        OrderRequest invalid = new OrderRequest("ORD-999", "", 1);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/messages/orders",
                invalid,
                String.class
        );

        // Then: 400 Bad Request (Bean Validation rejects the blank product)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/messages/orders returns 400 when quantity is zero")
    void publishOrder_returns400_whenQuantityIsZero() {
        // Given: an invalid request with quantity = 0 (violates @Min(1))
        OrderRequest invalid = new OrderRequest("ORD-888", "Product", 0);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/messages/orders",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/messages/orders returns 400 when quantity is negative")
    void publishOrder_returns400_whenQuantityIsNegative() {
        // Given: an invalid request with a negative quantity
        OrderRequest invalid = new OrderRequest("ORD-777", "Product", -5);

        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/messages/orders",
                invalid,
                String.class
        );

        // Then: 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── End-to-end: produce → broker → consume ────────────────────────────────────

    @Test
    @DisplayName("Published order is consumed by the JMS listener and appears in processed list")
    void publishOrder_isConsumedByListener() throws InterruptedException {
        // Given: a valid order request published via the service layer
        OrderRequest request = new OrderRequest("ORD-E2E-001", "Wireless Mouse", 5);

        // When: publish the order
        messageProducerService.publishOrder(request);

        // Then: wait up to 10 seconds for the async listener to process it
        waitForProcessing(countBefore + 1, 10_000);

        // Assert that the processed count increased by exactly 1
        assertThat(orderProcessingService.getProcessedOrderCount())
                .isGreaterThanOrEqualTo(countBefore + 1);

        // And: the processed list contains our message
        List<OrderMessage> processed = orderProcessingService.getProcessedOrders();
        boolean found = processed.stream()
                .anyMatch(m -> "ORD-E2E-001".equals(m.getOrderId())
                               && "Wireless Mouse".equals(m.getProduct())
                               && m.getQuantity() == 5);
        assertThat(found).as("Expected ORD-E2E-001 to appear in the processed orders list").isTrue();
    }

    @Test
    @DisplayName("Multiple published orders are all consumed and processed by the JMS listener")
    void multipleOrders_areAllConsumedByListener() throws InterruptedException {
        // Given: three different orders
        OrderRequest req1 = new OrderRequest("ORD-MULTI-A", "Product A", 1);
        OrderRequest req2 = new OrderRequest("ORD-MULTI-B", "Product B", 2);
        OrderRequest req3 = new OrderRequest("ORD-MULTI-C", "Product C", 3);

        // When: all three are published
        messageProducerService.publishOrder(req1);
        messageProducerService.publishOrder(req2);
        messageProducerService.publishOrder(req3);

        // Then: wait for all three to be processed (up to 15 seconds)
        waitForProcessing(countBefore + 3, 15_000);

        assertThat(orderProcessingService.getProcessedOrderCount())
                .isGreaterThanOrEqualTo(countBefore + 3);
    }

    @Test
    @DisplayName("GET /api/messages/orders returns 200 with the list of processed orders")
    void getProcessedOrders_returns200WithList() throws InterruptedException {
        // Given: publish one order and wait for it to be consumed
        messageProducerService.publishOrder(new OrderRequest("ORD-GET-001", "Keyboard", 1));
        waitForProcessing(countBefore + 1, 10_000);

        // When: GET the processed orders list via HTTP
        ResponseEntity<OrderMessage[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/messages/orders",
                OrderMessage[].class
        );

        // Then: 200 OK is returned with a non-null array
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // And: the array contains at least one element
        assertThat(response.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Service layer publishOrder returns a message with correct fields")
    void servicePublishOrder_returnsCorrectOrderMessage() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-SVC-001", "Headphones", 4);

        // When: call the service directly (bypassing HTTP)
        OrderMessage result = messageProducerService.publishOrder(request);

        // Then: the returned OrderMessage must carry the correct fields
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-SVC-001");
        assertThat(result.getProduct()).isEqualTo("Headphones");
        assertThat(result.getQuantity()).isEqualTo(4);
        // Auto-generated fields must always be populated
        assertThat(result.getMessageId()).isNotNull().isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
    }
}
