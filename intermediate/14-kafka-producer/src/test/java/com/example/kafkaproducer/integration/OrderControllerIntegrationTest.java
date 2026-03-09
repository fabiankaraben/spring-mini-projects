package com.example.kafkaproducer.integration;

import com.example.kafkaproducer.domain.OrderEvent;
import com.example.kafkaproducer.domain.OrderStatus;
import com.example.kafkaproducer.dto.PublishOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Kafka Producer REST API.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>HTTP contract: correct status codes, JSON response shape.</li>
 *   <li>Bean Validation: blank fields and invalid quantities return 400.</li>
 *   <li>End-to-end Kafka delivery: after a successful POST the event is
 *       actually readable from the Kafka topic using a real consumer.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers KRaft</strong> – starts a real single-broker
 *       Kafka container in KRaft mode (no ZooKeeper). The container image
 *       {@code confluentinc/cp-kafka} is used because the official Testcontainers
 *       Kafka module bundles it.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – injects the container's
 *       dynamic bootstrap address into the Spring context before it starts.</li>
 *   <li><strong>MockMvc</strong> – exercises the full Spring MVC stack without
 *       starting a real HTTP server.</li>
 *   <li><strong>Raw KafkaConsumer</strong> – a manually configured consumer
 *       verifies that the message was actually written to the broker.</li>
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
@DisplayName("OrderController – Integration Tests")
class OrderControllerIntegrationTest {

    /**
     * KRaft-mode Kafka container (no ZooKeeper).
     *
     * <p>Testcontainers pulls the {@code confluentinc/cp-kafka:7.6.1} image
     * (or uses the locally cached version). The container exposes port 9093
     * (the KRaft listener) and Testcontainers maps it to a random host port.
     *
     * <p>The {@code static} keyword is crucial: it ensures the container is
     * started once before any Spring context is created, so
     * {@link #overrideKafkaProperties} can inject the correct bootstrap
     * address before Spring's Kafka auto-configuration runs.
     */
    @Container
    @SuppressWarnings("resource")
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /**
     * Overrides the Spring Kafka bootstrap-servers property with the address
     * of the Testcontainers-managed broker.
     *
     * <p>Because Testcontainers maps port 9092 to a random host port, the
     * address cannot be hard-coded. {@code @DynamicPropertySource} solves this
     * by registering property overrides after the container starts but before
     * the Spring ApplicationContext is created.
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── HTTP contract tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders should return 202 Accepted for a valid request")
    void publishOrder_shouldReturn202_forValidRequest() throws Exception {
        PublishOrderRequest request = new PublishOrderRequest(
                "order-001", "cust-1", "Laptop", 1,
                new BigDecimal("999.99"), OrderStatus.CREATED);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                // The response must include the eventId field
                .andExpect(jsonPath("$.eventId").isNotEmpty())
                // orderId must be echoed from the request
                .andExpect(jsonPath("$.orderId").value("order-001"))
                // topic must be the configured topic name
                .andExpect(jsonPath("$.topic").value("order-events"))
                // Kafka metadata: partition and offset must be present
                .andExpect(jsonPath("$.partition").isNumber())
                .andExpect(jsonPath("$.offset").isNumber())
                // timestamp must be set
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/orders should return 202 for all valid OrderStatus values")
    void publishOrder_shouldReturn202_forAllStatuses() throws Exception {
        for (OrderStatus status : OrderStatus.values()) {
            PublishOrderRequest request = new PublishOrderRequest(
                    "order-status-" + status.name(), "cust-1", "Product",
                    1, new BigDecimal("10.00"), status);

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }
    }

    // ── Bean Validation tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders should return 400 for blank orderId")
    void publishOrder_shouldReturn400_forBlankOrderId() throws Exception {
        // Send a raw JSON string with a blank orderId to trigger @NotBlank
        String body = """
                {
                  "orderId": "",
                  "customerId": "cust-1",
                  "product": "Widget",
                  "quantity": 1,
                  "totalAmount": "10.00",
                  "status": "CREATED"
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.orderId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/orders should return 400 for quantity less than 1")
    void publishOrder_shouldReturn400_forZeroQuantity() throws Exception {
        String body = """
                {
                  "orderId": "order-q",
                  "customerId": "cust-1",
                  "product": "Widget",
                  "quantity": 0,
                  "totalAmount": "10.00",
                  "status": "CREATED"
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.quantity").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/orders should return 400 for totalAmount of zero")
    void publishOrder_shouldReturn400_forZeroTotalAmount() throws Exception {
        String body = """
                {
                  "orderId": "order-a",
                  "customerId": "cust-1",
                  "product": "Widget",
                  "quantity": 1,
                  "totalAmount": "0.00",
                  "status": "CREATED"
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.totalAmount").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/orders should return 400 when status is missing")
    void publishOrder_shouldReturn400_whenStatusIsMissing() throws Exception {
        String body = """
                {
                  "orderId": "order-s",
                  "customerId": "cust-1",
                  "product": "Widget",
                  "quantity": 1,
                  "totalAmount": "10.00"
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── End-to-end Kafka delivery test ────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders should deliver the event to the Kafka topic")
    void publishOrder_shouldDeliverEventToKafkaTopic() throws Exception {
        // Step 1: send the HTTP request to publish an order event
        PublishOrderRequest request = new PublishOrderRequest(
                "order-e2e-1", "cust-e2e", "SuperGadget", 2,
                new BigDecimal("49.98"), OrderStatus.CONFIRMED);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        // Extract the eventId from the response so we can verify it in Kafka
        String responseBody = result.getResponse().getContentAsString();
        String eventId = objectMapper.readTree(responseBody).get("eventId").asText();
        assertThat(eventId).isNotBlank();

        // Step 2: create a raw Kafka consumer to read back the message we just produced
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        // Unique group ID prevents conflicts between test runs
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Trust the OrderEvent class so JsonDeserializer can reconstruct it
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.kafkaproducer.domain");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class.getName());

        // Step 3: poll the topic and look for our event
        boolean found = false;
        try (KafkaConsumer<String, OrderEvent> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("order-events"));

            // Poll up to 10 seconds total looking for the message
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && !found) {
                ConsumerRecords<String, OrderEvent> records =
                        consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, OrderEvent> record : records) {
                    // The message key must be the orderId
                    if ("order-e2e-1".equals(record.key())) {
                        OrderEvent consumed = record.value();
                        // Verify the event contains the expected data
                        assertThat(consumed.orderId()).isEqualTo("order-e2e-1");
                        assertThat(consumed.customerId()).isEqualTo("cust-e2e");
                        assertThat(consumed.product()).isEqualTo("SuperGadget");
                        assertThat(consumed.quantity()).isEqualTo(2);
                        assertThat(consumed.status()).isEqualTo(OrderStatus.CONFIRMED);
                        // The eventId in the consumed message must match the one in the HTTP response
                        assertThat(consumed.eventId()).isEqualTo(eventId);
                        found = true;
                        break;
                    }
                }
            }
        }

        // Step 4: assert the message was found in Kafka
        assertThat(found)
                .as("Expected to find the order event in the Kafka topic within 10 seconds")
                .isTrue();
    }
}
