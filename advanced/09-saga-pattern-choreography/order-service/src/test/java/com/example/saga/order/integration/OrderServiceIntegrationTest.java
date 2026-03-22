package com.example.saga.order.integration;

import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderStatus;
import com.example.saga.order.events.OrderCreatedEvent;
import com.example.saga.order.events.PaymentFailedEvent;
import com.example.saga.order.events.PaymentProcessedEvent;
import com.example.saga.order.repository.OrderRepository;
import com.example.saga.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration test for the Order Service.
 *
 * <p>Uses Testcontainers to start real Docker containers:
 * <ul>
 *   <li>{@link PostgreSQLContainer} — real PostgreSQL for JPA/Hibernate persistence.</li>
 *   <li>{@link KafkaContainer} — real Kafka broker for event publishing.</li>
 * </ul>
 *
 * <p>Test coverage:
 * <ol>
 *   <li>POST /api/orders creates an order and publishes OrderCreatedEvent to real Kafka.</li>
 *   <li>handlePaymentProcessed updates order status in real PostgreSQL.</li>
 *   <li>handlePaymentFailed cancels order in real PostgreSQL.</li>
 * </ol>
 *
 * <p>The {@link DynamicPropertySource} annotation injects the Testcontainers-assigned
 * ports into Spring's property environment, overriding the values in application.yml
 * with the real container connection details.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Order Service Integration Tests")
class OrderServiceIntegrationTest {

    // -------------------------------------------------------------------------
    // Testcontainers — Docker containers shared for all tests in this class
    // -------------------------------------------------------------------------

    /**
     * PostgreSQL container for the order-service database.
     * Uses the official postgres:16-alpine image (small, well-supported on ARM64 + AMD64).
     */
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("orderdb")
                    .withUsername("saga")
                    .withPassword("saga");

    /**
     * Kafka container.
     * The confluentinc/cp-kafka image supports both AMD64 and ARM64.
     */
    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /**
     * Injects container connection details into the Spring application context
     * before it starts. This overrides the values in application.yml with the
     * dynamically allocated host ports assigned by Testcontainers.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Override datasource URL with the container's JDBC URL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Override Kafka bootstrap servers with the container's bootstrap address
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // -------------------------------------------------------------------------
    // Spring-injected components
    // -------------------------------------------------------------------------

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Test Kafka consumer — captures events published during tests
    // -------------------------------------------------------------------------

    /** Blocking queue that collects raw Kafka records consumed during a test. */
    private BlockingQueue<ConsumerRecord<String, String>> consumedRecords;

    /** Container that runs the test consumer thread. */
    private KafkaMessageListenerContainer<String, String> listenerContainer;

    @BeforeEach
    void setUpKafkaConsumer() {
        consumedRecords = new LinkedBlockingQueue<>();

        // Build a raw String-valued consumer so we can inspect JSON manually
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> factory =
                new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProps =
                new ContainerProperties("order.created", "payment.refund");
        listenerContainer = new KafkaMessageListenerContainer<>(factory, containerProps);
        listenerContainer.setupMessageListener((MessageListener<String, String>) consumedRecords::add);
        listenerContainer.start();

        // Wait until the consumer has been assigned partitions (ready to consume)
        ContainerTestUtils.waitForAssignment(listenerContainer, 2);
    }

    @AfterEach
    void tearDown() {
        listenerContainer.stop();
        orderRepository.deleteAll();
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    @DisplayName("createOrder persists order with PAYMENT_PROCESSING status in PostgreSQL")
    void createOrderPersistsWithCorrectStatus() {
        // Act
        Order order = orderService.createOrder("cust-integration", "prod-1", 2, new BigDecimal("39.98"));

        // Assert: order is persisted in the real database
        Optional<Order> found = orderRepository.findById(order.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(found.get().getCustomerId()).isEqualTo("cust-integration");
        assertThat(found.get().getProductId()).isEqualTo("prod-1");
        assertThat(found.get().getQuantity()).isEqualTo(2);
        assertThat(found.get().getTotalPrice()).isEqualByComparingTo("39.98");
    }

    @Test
    @DisplayName("createOrder publishes OrderCreatedEvent to real Kafka topic")
    void createOrderPublishesEventToKafka() throws Exception {
        // Act
        Order order = orderService.createOrder("cust-kafka-test", "prod-2", 1, new BigDecimal("19.99"));

        // Drain until we find the record for THIS order (previous tests may have left records)
        ConsumerRecord<String, String> record = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecord<String, String> candidate = consumedRecords.poll(500, TimeUnit.MILLISECONDS);
            if (candidate != null && order.getId().toString().equals(candidate.key())) {
                record = candidate;
                break;
            }
        }

        // Assert: event received on order.created topic
        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo("order.created");
        assertThat(record.key()).isEqualTo(order.getId().toString());

        // Assert: event body contains correct data
        String json = record.value();
        assertThat(json).contains("cust-kafka-test");
        assertThat(json).contains("prod-2");
    }

    @Test
    @DisplayName("handlePaymentProcessed updates order to INVENTORY_RESERVING in PostgreSQL")
    void handlePaymentProcessedUpdatesStatus() {
        // Arrange: create order first
        Order order = orderService.createOrder("cust-pay", "prod-3", 3, new BigDecimal("59.97"));
        UUID paymentId = UUID.randomUUID();

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                order.getId(), "cust-pay", paymentId, new BigDecimal("59.97"));

        // Act
        orderService.handlePaymentProcessed(event);

        // Assert: status updated in database
        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVING);
    }

    @Test
    @DisplayName("handlePaymentFailed cancels order with reason in PostgreSQL")
    void handlePaymentFailedCancelsOrder() {
        // Arrange
        Order order = orderService.createOrder("cust-fail", "prod-4", 1, new BigDecimal("9.99"));

        PaymentFailedEvent event = new PaymentFailedEvent(
                order.getId(), "cust-fail", "Card declined");

        // Act
        orderService.handlePaymentFailed(event);

        // Assert
        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getFailureReason()).contains("Card declined");
    }
}
