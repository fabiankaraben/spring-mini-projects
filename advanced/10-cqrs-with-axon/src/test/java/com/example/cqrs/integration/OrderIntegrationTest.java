package com.example.cqrs.integration;

import com.example.cqrs.command.api.PlaceOrderCommand;
import com.example.cqrs.query.api.FindOrderByIdQuery;
import com.example.cqrs.query.model.OrderSummary;
import com.example.cqrs.query.model.OrderSummaryRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the CQRS order system.
 *
 * <h2>What is tested here</h2>
 * <ul>
 *   <li>The complete command → event → projection pipeline with a real PostgreSQL database</li>
 *   <li>REST API endpoints (place, confirm, cancel, query)</li>
 *   <li>Axon event store persistence and aggregate reconstruction from events</li>
 *   <li>Read model (OrderSummary) updates via event projection</li>
 * </ul>
 *
 * <h2>Test infrastructure</h2>
 * <ul>
 *   <li>{@code @Testcontainers} + {@code @Container}: spins up a real PostgreSQL container
 *       (no H2 in-memory) to ensure the JPA event store and read model work correctly.</li>
 *   <li>{@code @DynamicPropertySource}: injects the Testcontainers JDBC URL into the
 *       Spring context before beans are created.</li>
 *   <li>{@code @SpringBootTest}: loads the full application context.</li>
 *   <li>{@code @AutoConfigureMockMvc}: configures MockMvc for HTTP-level testing.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Order CQRS Integration Tests")
class OrderIntegrationTest {

    // =========================================================================
    //  Testcontainers — PostgreSQL
    // =========================================================================

    /**
     * Shared PostgreSQL container — reused across all test methods in this class.
     *
     * <p>Using {@code @Container} on a {@code static} field means Testcontainers starts
     * the container once per test class (not per test method), which is much faster.
     *
     * <p>We use PostgreSQL 16 (Alpine variant) for a small image footprint.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("axondb_test")
            .withUsername("axon")
            .withPassword("axon");

    /**
     * Injects the dynamically assigned Testcontainers JDBC URL and credentials
     * into the Spring application context <em>before</em> beans are created.
     *
     * <p>This overrides the {@code spring.datasource.*} values in application-test.yml,
     * pointing the app at the running Testcontainers PostgreSQL instance.
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // =========================================================================
    //  Spring beans
    // =========================================================================

    /** MockMvc for HTTP-level REST testing. */
    @Autowired
    private MockMvc mockMvc;

    /** Axon command gateway — used in some tests to send commands directly. */
    @Autowired
    private CommandGateway commandGateway;

    /** Axon query gateway — used to verify the query side after commands. */
    @Autowired
    private QueryGateway queryGateway;

    /** JPA repository — used for direct read-model assertions. */
    @Autowired
    private OrderSummaryRepository orderSummaryRepository;

    // =========================================================================
    //  Setup
    // =========================================================================

    /**
     * Cleans the read-model table before each test so tests are independent.
     *
     * <p>Each test generates a fresh {@code UUID.randomUUID()} for the order ID, so there
     * is no risk of aggregate identifier collision in the event store across test methods.
     * Only the read model (order_summaries) is cleared to keep query-side assertions clean.
     */
    @BeforeEach
    void setUp() {
        orderSummaryRepository.deleteAll();
    }

    // =========================================================================
    //  REST API tests — full end-to-end
    // =========================================================================

    @Test
    @DisplayName("POST /api/orders should create an order and return 201")
    void shouldCreateOrderViaRestApi() throws Exception {
        // when: POST a new order via REST
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "PROD-001",
                                  "quantity": 5,
                                  "unitPrice": 19.99
                                }
                                """))
                // then: expect 201 Created with orderId in body
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Order placed successfully"));
    }

    @Test
    @DisplayName("POST /api/orders should reject invalid request (missing productId)")
    void shouldRejectInvalidPlaceOrderRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 5,
                                  "unitPrice": 19.99
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders should reject invalid request (zero quantity)")
    void shouldRejectZeroQuantityViaRest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "PROD-001",
                                  "quantity": 0,
                                  "unitPrice": 19.99
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/orders/{id} should return 404 for unknown order")
    void shouldReturn404ForUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/orders/unknown-id"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    //  Full command → projection pipeline tests
    // =========================================================================

    @Test
    @DisplayName("Placing an order should update the read model (projection)")
    void shouldUpdateReadModelAfterPlacingOrder() {
        // given: a unique order ID
        String orderId = UUID.randomUUID().toString();

        // when: send a PlaceOrderCommand via the command gateway
        commandGateway.sendAndWait(new PlaceOrderCommand(
                orderId, "PROD-123", 2, new BigDecimal("49.99")
        ));

        // then: the OrderSummary read model should be populated
        Optional<OrderSummary> summary = orderSummaryRepository.findById(orderId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getProductId()).isEqualTo("PROD-123");
        assertThat(summary.get().getQuantity()).isEqualTo(2);
        assertThat(summary.get().getUnitPrice()).isEqualByComparingTo("49.99");
        assertThat(summary.get().getStatus()).isEqualTo("PLACED");
        assertThat(summary.get().getPlacedAt()).isNotNull();
    }

    @Test
    @DisplayName("Confirming an order should update the read model to CONFIRMED")
    void shouldUpdateReadModelAfterConfirmingOrder() throws Exception {
        // given: place an order via REST and extract the orderId
        String responseBody = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "PROD-CONFIRM",
                                  "quantity": 1,
                                  "unitPrice": 9.99
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract orderId from response (simple string parsing for simplicity)
        String orderId = extractOrderId(responseBody);

        // when: confirm the order via REST
        mockMvc.perform(put("/api/orders/" + orderId + "/confirm"))
                .andExpect(status().isOk());

        // then: the read model should show CONFIRMED status
        Optional<OrderSummary> summary = orderSummaryRepository.findById(orderId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getStatus()).isEqualTo("CONFIRMED");
        assertThat(summary.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Cancelling an order should update the read model to CANCELLED")
    void shouldUpdateReadModelAfterCancellingOrder() throws Exception {
        // given: place an order via REST
        String responseBody = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "PROD-CANCEL",
                                  "quantity": 3,
                                  "unitPrice": 14.50
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = extractOrderId(responseBody);

        // when: cancel the order via REST
        mockMvc.perform(put("/api/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Changed my mind"}
                                """))
                .andExpect(status().isOk());

        // then: the read model should show CANCELLED status
        Optional<OrderSummary> summary = orderSummaryRepository.findById(orderId);
        assertThat(summary).isPresent();
        assertThat(summary.get().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("GET /api/orders/{id} should return the order after placement")
    void shouldReturnOrderViaQueryEndpoint() throws Exception {
        // given: place an order directly via command gateway
        String orderId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new PlaceOrderCommand(
                orderId, "PROD-QUERY", 7, new BigDecimal("3.50")
        ));

        // when: query via REST endpoint
        mockMvc.perform(get("/api/orders/" + orderId))
                // then: expect 200 with the order summary
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.productId").value("PROD-QUERY"))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    @DisplayName("GET /api/orders should return all placed orders")
    void shouldReturnAllOrdersViaQueryEndpoint() throws Exception {
        // given: create two orders
        String orderId1 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new PlaceOrderCommand(orderId1, "PROD-A", 1, new BigDecimal("10.00")));
        commandGateway.sendAndWait(new PlaceOrderCommand(orderId2, "PROD-B", 2, new BigDecimal("20.00")));

        // when: get all orders
        mockMvc.perform(get("/api/orders"))
                // then: expect at least 2 orders
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("GET /api/orders?status=PLACED should filter by status")
    void shouldFilterOrdersByStatus() throws Exception {
        // given: place one order and confirm another
        String placedOrderId = UUID.randomUUID().toString();
        String confirmedOrderId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new PlaceOrderCommand(placedOrderId, "PROD-P", 1, new BigDecimal("5.00")));
        commandGateway.sendAndWait(new PlaceOrderCommand(confirmedOrderId, "PROD-C", 1, new BigDecimal("5.00")));

        // Confirm the second order via REST
        mockMvc.perform(put("/api/orders/" + confirmedOrderId + "/confirm"))
                .andExpect(status().isOk());

        // when: filter by PLACED status
        mockMvc.perform(get("/api/orders?status=PLACED"))
                // then: only the non-confirmed order should be returned
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId",
                        org.hamcrest.Matchers.hasItem(placedOrderId)))
                .andExpect(jsonPath("$[*].orderId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(confirmedOrderId))));
    }

    @Test
    @DisplayName("Confirming a confirmed order should return 409 Conflict")
    void shouldReturn409WhenConfirmingAlreadyConfirmedOrder() throws Exception {
        // given: place and confirm an order
        String orderId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new PlaceOrderCommand(orderId, "PROD-X", 1, new BigDecimal("1.00")));
        mockMvc.perform(put("/api/orders/" + orderId + "/confirm")).andExpect(status().isOk());

        // when: try to confirm again
        mockMvc.perform(put("/api/orders/" + orderId + "/confirm"))
                // then: expect 409 Conflict
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cancelling a confirmed order should return 409 Conflict")
    void shouldReturn409WhenCancellingConfirmedOrder() throws Exception {
        // given: place and confirm an order
        String orderId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new PlaceOrderCommand(orderId, "PROD-Y", 1, new BigDecimal("1.00")));
        mockMvc.perform(put("/api/orders/" + orderId + "/confirm")).andExpect(status().isOk());

        // when: try to cancel the confirmed order
        mockMvc.perform(put("/api/orders/" + orderId + "/cancel"))
                // then: expect 409 Conflict
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("QueryGateway: FindOrderByIdQuery should return order summary")
    void shouldReturnOrderViaQueryGateway() {
        // given: place an order
        String orderId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new PlaceOrderCommand(
                orderId, "PROD-QG", 4, new BigDecimal("12.00")
        ));

        // when: query via QueryGateway directly
        Optional<OrderSummary> result = queryGateway.query(
                new FindOrderByIdQuery(orderId),
                ResponseTypes.optionalInstanceOf(OrderSummary.class)
        ).join();

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getProductId()).isEqualTo("PROD-QG");
        assertThat(result.get().getQuantity()).isEqualTo(4);
    }

    // =========================================================================
    //  Helper methods
    // =========================================================================

    /**
     * Extracts the {@code orderId} field from a JSON response body using simple string
     * manipulation. Avoids a Jackson dependency in the test class.
     *
     * @param json the JSON string, e.g. {@code {"orderId":"abc-123","message":"..."}}
     * @return the extracted orderId value
     */
    private String extractOrderId(String json) {
        // Simple extraction: find "orderId":"<value>" pattern
        int start = json.indexOf("\"orderId\":\"") + "\"orderId\":\"".length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}
