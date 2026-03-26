package com.example.debeziumcdc.integration;

import com.example.debeziumcdc.cdc.CdcOperation;
import com.example.debeziumcdc.cdc.ProductCdcEvent;
import com.example.debeziumcdc.domain.Product;
import com.example.debeziumcdc.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test for the Debezium CDC pipeline.
 *
 * <p>This test starts real Docker containers via Testcontainers:
 * <ol>
 *   <li><strong>PostgreSQL</strong> — the source database with logical replication enabled.</li>
 *   <li><strong>Kafka</strong> — the target message broker for CDC events.</li>
 * </ol>
 *
 * <p>The Spring application context starts with the real Debezium embedded engine,
 * real Kafka producer, and real JPA/PostgreSQL connection. This gives full confidence
 * that the CDC pipeline works end-to-end.
 *
 * <p>Pipeline verified:
 * <pre>
 *   REST API → PostgreSQL (WAL) → Debezium Engine → Kafka topic → Test consumer
 * </pre>
 *
 * <p>Important: The Debezium engine needs a few seconds to connect to the PostgreSQL
 * replication slot and start streaming. We use Awaitility-style polling
 * (via {@link BlockingQueue#poll(long, TimeUnit)}) to wait for events asynchronously.
 *
 * <p>PostgreSQL container customisation:
 * The standard {@code postgres:16} image does not have {@code wal_level=logical}
 * by default. The {@link PostgreSQLContainer} used here applies the required
 * command-line arguments to enable logical replication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Debezium CDC Integration Tests (real PostgreSQL + Kafka)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DebeziumCdcIntegrationTest {

    // =========================================================================
    // Testcontainers — real PostgreSQL with logical replication
    // =========================================================================

    /**
     * PostgreSQL container with logical replication enabled.
     *
     * <p>{@code wal_level=logical} is required for Debezium's pgoutput plugin.
     * It enables the creation of replication slots and publications that Debezium
     * uses to receive change events.
     *
     * <p>The {@code max_wal_senders} and {@code max_replication_slots} settings
     * ensure there are enough slots/senders available for the connector.
     *
     * <p>Declared as {@code static} so a single container is shared across all
     * test methods (faster — container starts once per test class).
     */
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("productdb")
            .withUsername("debezium")
            .withPassword("debezium")
            // Enable logical replication — required for Debezium's pgoutput plugin
            .withCommand(
                    "postgres",
                    "-c", "wal_level=logical",
                    "-c", "max_wal_senders=10",
                    "-c", "max_replication_slots=10"
            );

    // =========================================================================
    // Testcontainers — real Kafka broker
    // =========================================================================

    /**
     * Kafka container using the Confluent Platform image.
     * Supports both AMD64 and ARM64 (Apple Silicon).
     *
     * <p>Declared as {@code static} so a single container is shared across
     * all test methods in this class.
     */
    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    // =========================================================================
    // Dynamic property injection — wire containers to Spring context
    // =========================================================================

    /**
     * Injects the Testcontainers-assigned addresses into the Spring application
     * context before it starts.
     *
     * <p>This overrides the default {@code localhost:5432} and {@code localhost:9092}
     * with the dynamically-assigned ports of the Docker containers.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Override the DataSource URL with the Testcontainers PostgreSQL address
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Override Debezium connector connection details
        registry.add("debezium.connector.database.hostname", postgres::getHost);
        registry.add("debezium.connector.database.port",
                () -> String.valueOf(postgres.getMappedPort(5432)));
        registry.add("debezium.connector.database.user", postgres::getUsername);
        registry.add("debezium.connector.database.password", postgres::getPassword);
        registry.add("debezium.connector.database.dbname", postgres::getDatabaseName);

        // Override the Kafka bootstrap servers
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // =========================================================================
    // Spring-injected components
    // =========================================================================

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductService productService;

    // =========================================================================
    // Test-side Kafka consumer — receives CDC events published by the app
    // =========================================================================

    /**
     * A queue that collects CDC events received from Kafka during the test.
     * Test methods poll this queue to verify that Debezium emitted the expected event.
     */
    private static final BlockingQueue<ProductCdcEvent> receivedEvents = new LinkedBlockingQueue<>();

    /** Kafka listener container managed by the test (not by Spring). */
    private static KafkaMessageListenerContainer<String, ProductCdcEvent> listenerContainer;

    /**
     * Sets up a test-side Kafka consumer that listens on the CDC topic.
     *
     * <p>This consumer is separate from the application's {@code CdcEventConsumer}
     * and gives the test direct access to the raw events arriving in Kafka.
     *
     * <p>We build the consumer factory manually using the Testcontainers broker address.
     */
    @BeforeAll
    static void setUpKafkaConsumer() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "test-cdc-verifier");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.debeziumcdc.*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProductCdcEvent.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        DefaultKafkaConsumerFactory<String, ProductCdcEvent> factory =
                new DefaultKafkaConsumerFactory<>(config);

        ContainerProperties containerProps =
                new ContainerProperties("product-cdc-events");

        listenerContainer = new KafkaMessageListenerContainer<>(factory, containerProps);

        // Add received messages to the shared queue for test assertions
        listenerContainer.setupMessageListener(
                (MessageListener<String, ProductCdcEvent>) record ->
                        receivedEvents.add(record.value()));

        listenerContainer.start();

        // Wait until the listener has been assigned at least one partition
        // before the tests start sending messages. This prevents a race condition
        // where a message is sent before the consumer has joined the group.
        ContainerTestUtils.waitForAssignment(listenerContainer, 1);
    }

    @AfterAll
    static void tearDownKafkaConsumer() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @BeforeEach
    void clearQueue() {
        // Clear accumulated events between tests to prevent cross-test contamination
        receivedEvents.clear();
    }

    // =========================================================================
    // REST API tests (no Debezium assertion — fast, focused on HTTP layer)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /api/products returns 201 Created with product body and Location header")
    void postProductReturns201() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Test Laptop",
                "description", "A test laptop",
                "price", "999.99",
                "stock", 5
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Test Laptop"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/products returns 400 when name is blank")
    void postProductReturnsBadRequestForBlankName() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "",
                "price", "9.99",
                "stock", 1
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/products returns 400 when price is negative")
    void postProductReturnsBadRequestForNegativePrice() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Bad Product",
                "price", "-1.00",
                "stock", 0
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/products/{id} returns 404 for unknown ID")
    void getProductReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/products/{id} returns 200 with product after creation")
    void getProductReturnsProductAfterCreation() throws Exception {
        // Create a product via service (bypass REST to keep this test focused)
        Product product = productService.createProduct(
                "Headphones", "Noise-cancelling", new BigDecimal("199.99"), 15);

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.name").value("Headphones"))
                .andExpect(jsonPath("$.price").value(199.99));
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/products returns a list of all products")
    void listProductsReturnsAll() throws Exception {
        // Create two products via service
        productService.createProduct("Item A", null, new BigDecimal("10.00"), 1);
        productService.createProduct("Item B", null, new BigDecimal("20.00"), 2);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").isNotEmpty());
    }

    @Test
    @Order(7)
    @DisplayName("PUT /api/products/{id} returns 200 with updated product")
    void putProductReturnsUpdatedProduct() throws Exception {
        // Create a product first
        Product product = productService.createProduct(
                "Old Keyboard", null, new BigDecimal("49.99"), 3);

        Map<String, Object> updateBody = Map.of(
                "name", "New Keyboard",
                "price", "59.99",
                "stock", 10
        );

        mockMvc.perform(put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Keyboard"))
                .andExpect(jsonPath("$.price").value(59.99))
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    @Order(8)
    @DisplayName("PUT /api/products/{id} returns 404 for unknown ID")
    void putProductReturns404ForUnknownId() throws Exception {
        Map<String, Object> updateBody = Map.of("name", "Ghost");

        mockMvc.perform(put("/api/products/{id}", 99999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    @DisplayName("DELETE /api/products/{id} returns 204 after deletion")
    void deleteProductReturns204() throws Exception {
        Product product = productService.createProduct(
                "To Delete", null, new BigDecimal("1.00"), 0);

        mockMvc.perform(delete("/api/products/{id}", product.getId()))
                .andExpect(status().isNoContent());

        // Verify the product is gone
        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("DELETE /api/products/{id} returns 404 for unknown ID")
    void deleteProductReturns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 99999L))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // CDC pipeline tests — verify Debezium emits events to Kafka
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("CREATE: inserting a product emits a CREATE CDC event to Kafka")
    void insertProductEmitsCdcCreateEvent() throws InterruptedException {
        // Act: create a product via the service (triggers DB INSERT → WAL → Debezium → Kafka)
        Product product = productService.createProduct(
                "CDC Widget", "Created for CDC test", new BigDecimal("15.99"), 100);

        // Assert: wait up to 30s for a CREATE event for this product to arrive in Kafka.
        // Debezium may take a few seconds to start streaming after startup.
        ProductCdcEvent event = waitForEvent(CdcOperation.CREATE, product.getId(), 30);

        assertThat(event).as("Expected a CREATE CDC event but none arrived within 30s").isNotNull();
        assertThat(event.getOperation()).isIn(CdcOperation.CREATE, CdcOperation.READ);
        assertThat(event.getAfter()).isNotNull();
        assertThat(event.getAfter().getName()).isEqualTo("CDC Widget");
        assertThat(event.getAfter().getPrice()).isEqualByComparingTo("15.99");
        assertThat(event.getAfter().getStock()).isEqualTo(100);
    }

    @Test
    @Order(21)
    @DisplayName("UPDATE: updating a product emits an UPDATE CDC event to Kafka")
    void updateProductEmitsCdcUpdateEvent() throws InterruptedException {
        // Arrange: create a product first
        Product product = productService.createProduct(
                "CDC Updatable", null, new BigDecimal("10.00"), 5);

        // Drain any initial CREATE/snapshot events for this product
        waitForEvent(CdcOperation.CREATE, product.getId(), 30);

        // Act: update the product
        productService.updateProduct(product.getId(), "CDC Updated", null,
                new BigDecimal("20.00"), 50);

        // Assert: wait for an UPDATE event
        ProductCdcEvent updateEvent = waitForEvent(CdcOperation.UPDATE, product.getId(), 30);

        assertThat(updateEvent).as("Expected an UPDATE CDC event but none arrived within 30s").isNotNull();
        assertThat(updateEvent.getOperation()).isEqualTo(CdcOperation.UPDATE);
        assertThat(updateEvent.getAfter()).isNotNull();
        assertThat(updateEvent.getAfter().getName()).isEqualTo("CDC Updated");
        assertThat(updateEvent.getAfter().getPrice()).isEqualByComparingTo("20.00");
        assertThat(updateEvent.getAfter().getStock()).isEqualTo(50);
    }

    @Test
    @Order(22)
    @DisplayName("DELETE: deleting a product emits a DELETE CDC event to Kafka")
    void deleteProductEmitsCdcDeleteEvent() throws InterruptedException {
        // Arrange: create a product
        Product product = productService.createProduct(
                "CDC Deletable", null, new BigDecimal("5.00"), 1);

        // Drain the CREATE event
        waitForEvent(CdcOperation.CREATE, product.getId(), 30);

        Long productId = product.getId();

        // Act: delete the product
        productService.deleteProduct(productId);

        // Assert: wait for a DELETE event
        ProductCdcEvent deleteEvent = waitForEvent(CdcOperation.DELETE, productId, 30);

        assertThat(deleteEvent).as("Expected a DELETE CDC event but none arrived within 30s").isNotNull();
        assertThat(deleteEvent.getOperation()).isEqualTo(CdcOperation.DELETE);
        // For DELETE, the before snapshot contains the deleted row's values
        assertThat(deleteEvent.getBefore()).isNotNull();
        assertThat(deleteEvent.getBefore().getName()).isEqualTo("CDC Deletable");
        // For DELETE, the after snapshot is null (the row is gone)
        assertThat(deleteEvent.getAfter()).isNull();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Polls the {@link #receivedEvents} queue waiting for an event matching the
     * given operation and product ID.
     *
     * <p>We use a polling loop rather than a simple {@code poll(timeout)} because:
     * <ul>
     *   <li>The queue may contain events from other tests or snapshot reads.</li>
     *   <li>We need to find a specific event by product ID, not just any event.</li>
     * </ul>
     *
     * @param operation the expected CDC operation
     * @param productId the expected product ID (matches {@code after.id} for CREATE/UPDATE,
     *                  {@code before.id} for DELETE)
     * @param timeoutSeconds maximum time to wait in seconds
     * @return the matching event, or null if timeout elapsed
     */
    private ProductCdcEvent waitForEvent(CdcOperation operation, Long productId,
                                          int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            // Poll with a 500ms timeout to avoid busy-waiting
            ProductCdcEvent event = receivedEvents.poll(500, TimeUnit.MILLISECONDS);
            if (event == null) {
                continue;
            }

            // Match operation type
            boolean operationMatches = event.getOperation() == operation
                    || (operation == CdcOperation.CREATE && event.getOperation() == CdcOperation.READ);

            if (!operationMatches) {
                continue;
            }

            // Match product ID: for DELETE, ID is in 'before'; for others, in 'after'
            Long eventProductId = null;
            if (event.getAfter() != null) {
                eventProductId = event.getAfter().getId();
            } else if (event.getBefore() != null) {
                eventProductId = event.getBefore().getId();
            }

            if (productId.equals(eventProductId)) {
                return event;
            }
        }

        return null; // Timeout elapsed without finding a matching event
    }
}
