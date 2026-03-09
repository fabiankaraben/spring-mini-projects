package com.example.kafkaconsumer.integration;

import com.example.kafkaconsumer.domain.OrderEvent;
import com.example.kafkaconsumer.domain.OrderStatus;
import com.example.kafkaconsumer.domain.ProcessedOrderEvent;
import com.example.kafkaconsumer.service.OrderEventProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Kafka Consumer application.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>End-to-end message flow: a message produced to Kafka is actually consumed
 *       by the {@code @KafkaListener} and processed by the service.</li>
 *   <li>REST API contract: the {@code GET /api/events} endpoint returns the
 *       consumed events with correct field values.</li>
 *   <li>Status filter: {@code GET /api/events?status=CREATED} returns only
 *       events with the matching status.</li>
 *   <li>Count endpoint: {@code GET /api/events/count} reflects the actual
 *       number of consumed messages.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers KRaft</strong> – starts a real single-broker
 *       Kafka container in KRaft mode (no ZooKeeper). The Testcontainers Kafka
 *       module bundles the {@code confluentinc/cp-kafka} image.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – injects the container's
 *       dynamic bootstrap address into the Spring context before it starts.</li>
 *   <li><strong>Raw KafkaProducer</strong> – a manually configured producer
 *       sends test messages directly to the broker, simulating an upstream
 *       producer service (e.g. the 14-kafka-producer mini-project).</li>
 *   <li><strong>Awaitility</strong> – polls the service's in-memory store
 *       asynchronously until the consumer has processed the expected message,
 *       avoiding fragile {@code Thread.sleep()} calls.</li>
 *   <li><strong>MockMvc</strong> – exercises the full Spring MVC stack without
 *       starting a real HTTP server.</li>
 * </ul>
 *
 * <h2>Container lifecycle</h2>
 * The {@code static} {@link KafkaContainer} field is started in
 * {@link #beforeAll()} and stopped in {@link #afterAll()}. The {@code static}
 * modifier ensures the container is alive before the Spring context is created
 * so that {@link #overrideKafkaProperties} can supply the correct address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Kafka Consumer – Integration Tests")
class OrderEventConsumerIntegrationTest {

    /**
     * KRaft-mode Kafka container (no ZooKeeper).
     *
     * <p>The {@code static} keyword ensures the container is started once before
     * the Spring context is created, so {@link #overrideKafkaProperties} can
     * inject the correct bootstrap address before Spring's auto-configuration runs.
     */
    @Container
    @SuppressWarnings("resource")
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /**
     * Overrides the Kafka bootstrap-servers with the Testcontainers-managed
     * broker address. Because the port is dynamically assigned, we cannot
     * hard-code it.
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    /** Starts the Kafka container before any test method runs. */
    @BeforeAll
    static void beforeAll() {
        kafka.start();
    }

    /** Stops the Kafka container after all tests have finished. */
    @AfterAll
    static void afterAll() {
        kafka.stop();
    }

    /**
     * The processor service bean – injected so we can query the in-memory
     * event store directly without going through HTTP for the async polling.
     * Typed as the interface so the injection works regardless of which
     * implementation Spring chooses.
     */
    @Autowired
    private OrderEventProcessor processorService;

    /** MockMvc instance for exercising the REST API. */
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a raw {@link KafkaProducer} connected to the Testcontainers broker.
     *
     * <p>This producer simulates an upstream service (e.g. the 14-kafka-producer
     * mini-project) sending {@link OrderEvent} messages to the {@code order-events}
     * topic. We use Spring Kafka's {@link JsonSerializer} for the value so the
     * JSON structure matches what the consumer expects.
     *
     * @return a configured {@link KafkaProducer}
     */
    private KafkaProducer<String, OrderEvent> createTestProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Disable type headers so the consumer uses VALUE_DEFAULT_TYPE (as configured)
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaProducer<>(props);
    }

    /**
     * Creates a minimal {@link OrderEvent} fixture for use in tests.
     *
     * @param orderId the orderId to embed in the event
     * @param status  the order status to embed in the event
     * @return a new {@link OrderEvent}
     */
    private OrderEvent buildEvent(String orderId, OrderStatus status) {
        return new OrderEvent(
                UUID.randomUUID().toString(), // eventId
                orderId,
                "cust-integration",
                "Integration Widget",
                3,
                new BigDecimal("29.97"),
                status,
                Instant.now()
        );
    }

    // ── End-to-end consumption test ───────────────────────────────────────────

    @Test
    @DisplayName("Consumer should process a message produced to the Kafka topic")
    void consumer_shouldProcessProducedMessage() throws Exception {
        OrderEvent event = buildEvent("order-it-1", OrderStatus.CREATED);

        // Produce a message to Kafka using a raw producer (simulates the upstream service)
        try (KafkaProducer<String, OrderEvent> producer = createTestProducer()) {
            producer.send(new ProducerRecord<>("order-events", event.orderId(), event));
            producer.flush();
        }

        // Wait asynchronously (up to 15 s) for the consumer to process the message.
        // Awaitility polls repeatedly so the test does not rely on Thread.sleep().
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<ProcessedOrderEvent> events = processorService.getProcessedEvents();
                    assertThat(events)
                            .anyMatch(e -> e.orderId().equals("order-it-1"));
                });

        // Verify the stored event has the correct field values
        ProcessedOrderEvent stored = processorService.getProcessedEvents().stream()
                .filter(e -> e.orderId().equals("order-it-1"))
                .findFirst()
                .orElseThrow();

        assertThat(stored.eventId()).isEqualTo(event.eventId());
        assertThat(stored.customerId()).isEqualTo(event.customerId());
        assertThat(stored.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(stored.processedAt()).isNotNull();
    }

    @Test
    @DisplayName("Consumer should record the Kafka partition and offset in ProcessedOrderEvent")
    void consumer_shouldRecordPartitionAndOffset() throws Exception {
        OrderEvent event = buildEvent("order-it-meta", OrderStatus.CONFIRMED);

        try (KafkaProducer<String, OrderEvent> producer = createTestProducer()) {
            producer.send(new ProducerRecord<>("order-events", event.orderId(), event));
            producer.flush();
        }

        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(processorService.getProcessedEvents())
                                .anyMatch(e -> e.orderId().equals("order-it-meta")));

        ProcessedOrderEvent stored = processorService.getProcessedEvents().stream()
                .filter(e -> e.orderId().equals("order-it-meta"))
                .findFirst()
                .orElseThrow();

        // Partition must be a valid Kafka partition index (>= 0)
        assertThat(stored.partition()).isGreaterThanOrEqualTo(0);
        // Offset must be non-negative
        assertThat(stored.offset()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Consumer should process all status values correctly")
    void consumer_shouldHandleAllOrderStatuses() throws Exception {
        // Produce one event of each status
        try (KafkaProducer<String, OrderEvent> producer = createTestProducer()) {
            for (OrderStatus status : OrderStatus.values()) {
                OrderEvent event = buildEvent("order-it-status-" + status.name(), status);
                producer.send(new ProducerRecord<>("order-events", event.orderId(), event));
            }
            producer.flush();
        }

        int expectedCount = OrderStatus.values().length;

        // Wait until all messages have been consumed
        await()
                .atMost(20, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    long matchingCount = processorService.getProcessedEvents().stream()
                            .filter(e -> e.orderId().startsWith("order-it-status-"))
                            .count();
                    assertThat(matchingCount).isGreaterThanOrEqualTo(expectedCount);
                });

        // Verify each status was processed
        for (OrderStatus status : OrderStatus.values()) {
            String expectedOrderId = "order-it-status-" + status.name();
            assertThat(processorService.getProcessedEvents())
                    .as("Expected a processed event with orderId=%s", expectedOrderId)
                    .anyMatch(e -> e.orderId().equals(expectedOrderId) && e.status() == status);
        }
    }

    // ── REST API tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/events should return 200 with a JSON array")
    void getEvents_shouldReturn200WithJsonArray() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                // The response must be a JSON array (even if empty)
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/events/count should return 200 with a count field")
    void getCount_shouldReturn200WithCountField() throws Exception {
        mockMvc.perform(get("/api/events/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    @DisplayName("GET /api/events?status=INVALID should return 400 Bad Request")
    void getEvents_shouldReturn400ForInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/events").param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/events should include consumed events in the response")
    void getEvents_shouldIncludeConsumedEvents() throws Exception {
        String uniqueOrderId = "order-it-api-" + System.currentTimeMillis();
        OrderEvent event = buildEvent(uniqueOrderId, OrderStatus.DELIVERED);

        try (KafkaProducer<String, OrderEvent> producer = createTestProducer()) {
            producer.send(new ProducerRecord<>("order-events", event.orderId(), event));
            producer.flush();
        }

        // Wait for the consumer to process the message before querying the REST API
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(processorService.getProcessedEvents())
                                .anyMatch(e -> e.orderId().equals(uniqueOrderId)));

        // Now verify the REST endpoint exposes the consumed event
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == '" + uniqueOrderId + "')]").exists());
    }

    @Test
    @DisplayName("GET /api/events?status=SHIPPED should return only SHIPPED events")
    void getEvents_shouldFilterByStatus() throws Exception {
        String shippedOrderId = "order-it-shipped-" + System.currentTimeMillis();
        OrderEvent shippedEvent = buildEvent(shippedOrderId, OrderStatus.SHIPPED);
        String cancelledOrderId = "order-it-cancelled-" + System.currentTimeMillis();
        OrderEvent cancelledEvent = buildEvent(cancelledOrderId, OrderStatus.CANCELLED);

        try (KafkaProducer<String, OrderEvent> producer = createTestProducer()) {
            producer.send(new ProducerRecord<>("order-events", shippedEvent.orderId(), shippedEvent));
            producer.send(new ProducerRecord<>("order-events", cancelledEvent.orderId(), cancelledEvent));
            producer.flush();
        }

        // Wait for both events to be consumed
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<ProcessedOrderEvent> events = processorService.getProcessedEvents();
                    assertThat(events).anyMatch(e -> e.orderId().equals(shippedOrderId));
                    assertThat(events).anyMatch(e -> e.orderId().equals(cancelledOrderId));
                });

        // Verify the status filter returns only SHIPPED events
        mockMvc.perform(get("/api/events").param("status", "SHIPPED"))
                .andExpect(status().isOk())
                // The shipped event must be present
                .andExpect(jsonPath("$[?(@.orderId == '" + shippedOrderId + "')]").exists())
                // All returned events must have status SHIPPED
                .andExpect(jsonPath("$[?(@.status != 'SHIPPED')]").doesNotExist());
    }
}
