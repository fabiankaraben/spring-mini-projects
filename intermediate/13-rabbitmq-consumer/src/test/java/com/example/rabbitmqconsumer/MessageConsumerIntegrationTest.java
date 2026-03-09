package com.example.rabbitmqconsumer;

import com.example.rabbitmqconsumer.domain.OrderMessage;
import com.example.rabbitmqconsumer.service.MessageConsumerService;
import com.example.rabbitmqconsumer.service.MessageConsumerService.ProcessedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for the RabbitMQ Consumer application.
 *
 * <p>This test class verifies end-to-end behaviour from the AMQP queue through
 * the {@code @RabbitListener} listener, down to the processed-message log and
 * the REST endpoints — all against a real RabbitMQ instance managed by
 * Testcontainers.
 *
 * <h2>Key aspects</h2>
 * <ul>
 *   <li>{@link SpringBootTest.WebEnvironment#RANDOM_PORT} starts a real embedded
 *       servlet container on a random port, so the full Spring MVC filter chain
 *       and the AMQP listener container are both active — identical to the
 *       production runtime.</li>
 *   <li>{@link Testcontainers} and {@link Container} spin up a RabbitMQ Docker
 *       container shared across all tests in this class. Sharing avoids the
 *       overhead of restarting RabbitMQ for each test method.</li>
 *   <li>{@link DynamicPropertySource} injects the container's dynamic host/port
 *       into the Spring {@code Environment} before the application context starts,
 *       so Spring Boot's {@code CachingConnectionFactory} connects to the
 *       Testcontainers-managed broker.</li>
 *   <li>The {@code "test"} profile activates {@code application-test.yml}, which
 *       reduces logging verbosity during test runs.</li>
 * </ul>
 *
 * <h2>How we publish messages in tests</h2>
 * <p>We use the auto-configured {@link RabbitTemplate} (configured with a JSON
 * converter in {@link com.example.rabbitmqconsumer.config.RabbitMQConfig}) to
 * publish messages directly to the exchange. The consumer's
 * {@code @RabbitListener} then picks them up asynchronously.
 *
 * <h2>How we verify consumption</h2>
 * <p>After publishing, we poll {@link MessageConsumerService#getProcessedMessages()}
 * with a short timeout (using {@link #waitForMessages}) to give the listener
 * container time to deliver and process the message asynchronously.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("RabbitMQ Consumer integration tests")
class MessageConsumerIntegrationTest {

    // ── Testcontainers RabbitMQ container ─────────────────────────────────────────

    /**
     * A RabbitMQ container shared by all tests in this class.
     *
     * <p>{@code static} is crucial: JUnit 5 + Testcontainers reuses one container
     * instance for the entire test class lifecycle, avoiding the significant
     * overhead of restarting RabbitMQ for each test method.
     * The container is stopped automatically after the last test finishes.
     */
    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    /**
     * Register the container's dynamic connection properties into the Spring
     * {@link org.springframework.core.env.Environment} before the application
     * context is created.
     *
     * <p>Testcontainers assigns random host ports to the container at startup,
     * so we must discover them at runtime via the container's getter methods.
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
     * {@link TestRestTemplate} pre-configured to connect to the embedded test
     * server. Does NOT throw on 4xx/5xx responses — ideal for asserting error paths.
     */
    @Autowired
    TestRestTemplate restTemplate;

    /**
     * The {@link MessageConsumerService} bean. We access it directly to inspect
     * the in-memory processed-message log after the listener has consumed messages.
     */
    @Autowired
    MessageConsumerService consumerService;

    /**
     * {@link RabbitTemplate} used in tests to <em>publish</em> messages to the
     * exchange so the consumer's {@code @RabbitListener} picks them up.
     * This simulates what the producer application would do.
     */
    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * {@link RabbitAdmin} for queue management operations in tests:
     * purging queues before each test to ensure isolation.
     */
    @Autowired
    RabbitAdmin rabbitAdmin;

    /** Exchange name, read from {@code application-test.yml}. */
    @Value("${spring.rabbitmq.exchange}")
    String exchangeName;

    /** Routing key for the orders queue. */
    @Value("${spring.rabbitmq.routing-key}")
    String routingKey;

    /** Queue name for the orders queue. */
    @Value("${spring.rabbitmq.queue}")
    String queueName;

    /** Dead-letter queue name. */
    @Value("${spring.rabbitmq.dead-letter-queue}")
    String deadLetterQueueName;

    // ── Per-test setup ─────────────────────────────────────────────────────────────

    /**
     * Reset the consumer service state and purge both queues before each test.
     *
     * <p>This ensures each test starts from a clean slate — no leftover messages
     * in the queue and no stale entries in the in-memory log.
     */
    @BeforeEach
    void setUp() {
        // Reset the in-memory processed-message log and counter
        consumerService.reset();
        // Purge the main queue (true = wait for the purge to complete)
        rabbitAdmin.purgeQueue(queueName, true);
        // Purge the dead-letter queue
        rabbitAdmin.purgeQueue(deadLetterQueueName, true);
    }

    // ── @RabbitListener consumption tests ─────────────────────────────────────────

    @Test
    @DisplayName("Consumer processes a published message and adds it to the log")
    void consumer_processesPublishedMessage() {
        // Given: a valid order message
        OrderMessage message = buildMessage("msg-it-1", "ORD-IT-001", "Integration Laptop", 2);

        // When: publish to the exchange (simulates the producer application)
        rabbitTemplate.convertAndSend(exchangeName, routingKey, message);

        // Then: wait up to 5 seconds for the @RabbitListener to process the message
        waitForMessages(1, 5000);

        // And: the message must appear in the processed log
        List<ProcessedMessage> processed = consumerService.getProcessedMessages();
        assertThat(processed).hasSize(1);
        assertThat(processed.get(0).orderId()).isEqualTo("ORD-IT-001");
        assertThat(processed.get(0).product()).isEqualTo("Integration Laptop");
        assertThat(processed.get(0).quantity()).isEqualTo(2);
        assertThat(processed.get(0).messageId()).isEqualTo("msg-it-1");
    }

    @Test
    @DisplayName("Consumer increments the message count for each processed message")
    void consumer_incrementsMessageCount() {
        // When: publish three messages
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("id1", "ORD-COUNT-1", "Product A", 1));
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("id2", "ORD-COUNT-2", "Product B", 3));
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("id3", "ORD-COUNT-3", "Product C", 5));

        // Then: wait for all three to be processed
        waitForMessages(3, 8000);

        // And: the count must be 3
        assertThat(consumerService.getMessageCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Consumer processes multiple messages and all appear in the log")
    void consumer_processesMultipleMessages_allInLog() {
        // When: publish two messages
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("m1", "ORD-MULTI-1", "Webcam", 1));
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("m2", "ORD-MULTI-2", "Headset", 2));

        // Then: wait for both to be processed
        waitForMessages(2, 8000);

        // And: both must be in the log
        List<ProcessedMessage> log = consumerService.getProcessedMessages();
        assertThat(log).hasSize(2);
        assertThat(log).extracting(ProcessedMessage::orderId)
                .containsExactlyInAnyOrder("ORD-MULTI-1", "ORD-MULTI-2");
    }

    @Test
    @DisplayName("Consumer sets a non-null processedAt timestamp on each processed message")
    void consumer_setsProcessedAtTimestamp() {
        // Given: record time boundaries
        Instant before = Instant.now();

        // When
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("ts-id", "ORD-TS", "Monitor", 1));
        waitForMessages(1, 5000);

        Instant after = Instant.now();

        // Then: processedAt must fall within the [before, after] window
        ProcessedMessage record = consumerService.getProcessedMessages().get(0);
        assertThat(record.processedAt())
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // ── REST API tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/messages/stats returns 200 with totalProcessed count")
    void getStats_returns200WithCount() {
        // Given: publish and wait for one message to be consumed
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("stat-id", "ORD-STAT", "Keyboard", 1));
        waitForMessages(1, 5000);

        // When: query the stats endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/messages/stats",
                Map.class
        );

        // Then: 200 OK with the correct total count
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("totalProcessed")).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/messages/stats returns 0 when no messages have been processed")
    void getStats_returnsZeroWhenNoMessages() {
        // Given: fresh state (setUp already reset the service)

        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/messages/stats",
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("totalProcessed")).isEqualTo(0);
    }

    @Test
    @DisplayName("GET /api/messages/processed returns 200 with the processed message list")
    void getProcessed_returns200WithList() {
        // Given: publish and consume one message
        rabbitTemplate.convertAndSend(exchangeName, routingKey,
                buildMessage("proc-id", "ORD-PROC", "USB Hub", 4));
        waitForMessages(1, 5000);

        // When
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/messages/processed",
                List.class
        );

        // Then: 200 OK with one entry
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
    }

    @Test
    @DisplayName("GET /api/messages/processed returns an empty list when no messages consumed")
    void getProcessed_returnsEmptyListWhenNoMessages() {
        // Given: fresh state from setUp

        // When
        ResponseEntity<List> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/messages/processed",
                List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().isEmpty();
    }

    // ── Helper methods ────────────────────────────────────────────────────────────

    /**
     * Build an {@link OrderMessage} with the given fields.
     *
     * @param messageId the message UUID
     * @param orderId   the business order ID
     * @param product   the product name
     * @param quantity  the number of units
     * @return a populated {@link OrderMessage}
     */
    private OrderMessage buildMessage(String messageId, String orderId,
                                      String product, int quantity) {
        return new OrderMessage(messageId, orderId, product, quantity, Instant.now());
    }

    /**
     * Poll the processed-message log until it contains at least {@code expectedCount}
     * entries, or until the {@code timeoutMs} elapses.
     *
     * <p>Because {@code @RabbitListener} is asynchronous, there is a non-deterministic
     * delay between the moment we publish a message and the moment the listener
     * finishes processing it. Polling with a timeout is the standard approach for
     * testing asynchronous systems without hard-coded {@code Thread.sleep} calls.
     *
     * @param expectedCount the minimum number of processed messages to wait for
     * @param timeoutMs     maximum milliseconds to wait before giving up
     */
    private void waitForMessages(int expectedCount, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        // Poll every 100 ms until the expected count is reached or the deadline passes
        while (System.currentTimeMillis() < deadline) {
            if (consumerService.getMessageCount() >= expectedCount) {
                return; // Expected number of messages have been processed
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        // If we reach here the timeout expired — the assertThat in the test will fail
        // with a clear message about the actual vs expected count.
    }
}
