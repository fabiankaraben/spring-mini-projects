package com.example.rabbitmqproducer;

import com.example.rabbitmqproducer.domain.OrderMessage;
import com.example.rabbitmqproducer.dto.OrderRequest;
import com.example.rabbitmqproducer.service.MessageProducerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the RabbitMQ Producer application.
 *
 * <p>This test class verifies end-to-end behaviour from the HTTP layer through
 * the service layer down to a real RabbitMQ instance managed by Testcontainers.
 *
 * <h2>Key aspects</h2>
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full Spring MVC filter chain,
 *       serialisation stack, and exception handlers are active — identical to
 *       production runtime.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a RabbitMQ Docker
 *       container for the duration of the test class. The container is shared
 *       across all test methods to avoid the overhead of restarting RabbitMQ
 *       for each individual test.</li>
 *   <li>{@link DynamicPropertySource} overrides the RabbitMQ connection properties
 *       in the Spring {@code Environment} before the application context is created,
 *       so Spring Boot connects to the Testcontainers-managed RabbitMQ container
 *       instead of any locally installed broker.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml} which
 *       reduces logging noise during test runs.</li>
 * </ul>
 *
 * <h2>How we verify that messages were actually published</h2>
 * <p>After calling the HTTP endpoint, we use {@link RabbitTemplate#receive} to
 * pull the next available message from the queue and inspect its content.
 * This verifies the full round-trip: HTTP → Service → RabbitMQ exchange → Queue.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("RabbitMQ Producer integration tests")
class MessageProducerIntegrationTest {

    // ── Testcontainers RabbitMQ container ─────────────────────────────────────────

    /**
     * A RabbitMQ container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses a single
     * container instance for the entire test class lifecycle, avoiding the
     * significant overhead of starting/stopping RabbitMQ for each test method.
     * The container is stopped automatically after the last test finishes.
     *
     * <p>We use the {@code rabbitmq:3.13-management-alpine} image which includes
     * the RabbitMQ Management HTTP API — useful for debugging during test runs.
     * The {@code alpine} base keeps the image size small.
     */
    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /**
     * Register the container's dynamic connection properties into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>Testcontainers assigns random host ports to the container, so we use
     * the container's getter methods to discover them at runtime. These properties
     * override the values in {@code application-test.yml}.
     *
     * @param registry the property registry that Spring Boot reads before context startup
     */
    @DynamicPropertySource
    static void rabbitMQProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    // ── Injected Spring beans ─────────────────────────────────────────────────────

    /** The random port chosen by Spring Boot for the embedded servlet container. */
    @LocalServerPort
    int port;

    /**
     * {@link TestRestTemplate} is pre-configured to connect to the embedded
     * test server. It does NOT throw exceptions on 4xx/5xx responses, which
     * makes it ideal for asserting error scenarios.
     */
    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Direct access to the service layer for verifying behaviour at a layer below
     * the HTTP interface.
     */
    @Autowired
    MessageProducerService messageProducerService;

    /**
     * {@link RabbitTemplate} is used in tests to <em>receive</em> messages from
     * the queue, allowing us to assert that the producer actually delivered the
     * expected payload to RabbitMQ.
     *
     * <p>In production, only consumers (not producers) call {@code receive}.
     * Here we use it purely as a test spy.
     */
    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * {@link RabbitAdmin} provides administrative operations on the broker:
     * inspecting queue depth, purging queues, etc. We use it here to purge
     * the queue between tests to ensure isolation.
     */
    @Autowired
    RabbitAdmin rabbitAdmin;

    /**
     * The queue bean declared in {@link com.example.rabbitmqproducer.config.RabbitMQConfig}.
     * Used to query the queue's message count via {@link RabbitAdmin}.
     */
    @Autowired
    Queue ordersQueue;

    /** The queue name, injected from {@code application-test.yml}. */
    @Value("${spring.rabbitmq.queue}")
    String queueName;

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

        // Clean up: remove the message from the queue so it does not affect other tests
        rabbitAdmin.purgeQueue(queueName, true);
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

    // ── Message actually arrives in the queue ─────────────────────────────────────

    @Test
    @DisplayName("Published order message is actually delivered to the RabbitMQ queue")
    void publishOrder_messageArrivesInQueue() {
        // Given: a valid order request published via the service layer
        OrderRequest request = new OrderRequest("ORD-QUEUE-001", "Wireless Mouse", 5);
        messageProducerService.publishOrder(request);

        // When: pull the next message from the queue using RabbitTemplate.receive
        // The second argument is a timeout in milliseconds – we wait up to 5 seconds
        // to account for any slight delay in broker delivery.
        Message rawMessage = rabbitTemplate.receive(queueName, 5000);

        // Then: a message must have arrived
        assertThat(rawMessage).isNotNull();

        // And: the raw message body must contain the expected JSON fields
        String body = new String(rawMessage.getBody());
        assertThat(body).contains("ORD-QUEUE-001");
        assertThat(body).contains("Wireless Mouse");
        assertThat(body).contains("5");
    }

    @Test
    @DisplayName("Queue message count increases by 1 after each published order")
    void publishOrder_incrementsQueueMessageCount() {
        // Given: note the current message count (should be 0 if tests run in isolation)
        rabbitAdmin.purgeQueue(queueName, true);

        Properties beforeProps = rabbitAdmin.getQueueProperties(queueName);
        int countBefore = beforeProps != null
                ? (int) beforeProps.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)
                : 0;

        // When: publish one order via the service layer
        messageProducerService.publishOrder(new OrderRequest("ORD-COUNT-001", "USB Hub", 1));

        // Allow a brief moment for the broker to process the publish
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: the queue should have exactly one more message
        Properties afterProps = rabbitAdmin.getQueueProperties(queueName);
        assertThat(afterProps).isNotNull();
        int countAfter = (int) afterProps.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        assertThat(countAfter).isEqualTo(countBefore + 1);

        // Clean up
        rabbitAdmin.purgeQueue(queueName, true);
    }

    @Test
    @DisplayName("Multiple published orders all arrive in the queue")
    void publishOrder_multipleMessages_allArriveInQueue() {
        // Given: purge the queue to start from a known state
        rabbitAdmin.purgeQueue(queueName, true);

        // When: publish three orders
        messageProducerService.publishOrder(new OrderRequest("ORD-MULTI-1", "Product A", 1));
        messageProducerService.publishOrder(new OrderRequest("ORD-MULTI-2", "Product B", 2));
        messageProducerService.publishOrder(new OrderRequest("ORD-MULTI-3", "Product C", 3));

        // Allow a brief moment for all messages to be acknowledged by the broker
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: all three messages must be in the queue
        Properties props = rabbitAdmin.getQueueProperties(queueName);
        assertThat(props).isNotNull();
        int messageCount = (int) props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        assertThat(messageCount).isEqualTo(3);

        // Clean up
        rabbitAdmin.purgeQueue(queueName, true);
    }

    @Test
    @DisplayName("Service layer publishOrder returns message with the correct fields")
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

        // Clean up
        rabbitAdmin.purgeQueue(queueName, true);
    }
}
