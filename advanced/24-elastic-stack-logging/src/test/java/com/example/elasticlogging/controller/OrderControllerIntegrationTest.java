package com.example.elasticlogging.controller;

import com.example.elasticlogging.dto.CreateOrderRequest;
import com.example.elasticlogging.dto.UpdateOrderStatusRequest;
import com.example.elasticlogging.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for {@link OrderController}.
 *
 * <p>These tests load the complete Spring application context (including all
 * beans, filters, and exception handlers) and exercise each REST endpoint
 * through MockMvc — an in-process HTTP layer that does not require a real
 * network socket.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>POST /api/orders — creates an order, returns 201</li>
 *   <li>GET  /api/orders — lists all orders, returns 200</li>
 *   <li>GET  /api/orders/{id} — retrieves order, returns 200; 404 for unknown</li>
 *   <li>PATCH /api/orders/{id}/status — updates status, returns 200</li>
 *   <li>POST /api/orders/{id}/fail — simulates failure, returns 200</li>
 *   <li>Validation errors — returns 400 with violation details</li>
 * </ul>
 *
 * <h2>Why @DirtiesContext</h2>
 * The {@link com.example.elasticlogging.service.OrderService} uses an in-memory
 * {@code ConcurrentHashMap} as its order store. Without {@code @DirtiesContext},
 * orders created in one test would be visible in subsequent tests, causing
 * non-deterministic failures. {@code @DirtiesContext} forces Spring to create a
 * fresh application context (and thus a fresh, empty store) for each test method.
 *
 * <h2>Testcontainers note</h2>
 * This class does NOT use Testcontainers directly because the Order API itself
 * has no external infrastructure dependencies (it uses an in-memory store).
 * Testcontainers integration with Elasticsearch is demonstrated in
 * {@link ElasticsearchIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("OrderController integration tests")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // POST /api/orders
    // =========================================================================

    @Test
    @DisplayName("POST /api/orders: creates order and returns 201 with order body")
    void createOrder_returns201WithOrderBody() throws Exception {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-001", "Laptop model X", new BigDecimal("1299.99"));

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("customer-001"))
                .andExpect(jsonPath("$.description").value("Laptop model X"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/orders: returns 400 when customerId is blank")
    void createOrder_returns400WhenCustomerIdBlank() throws Exception {
        // Arrange — customerId is empty string (violates @NotBlank)
        CreateOrderRequest request = new CreateOrderRequest(
                "", "Laptop", new BigDecimal("999.00"));

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations").exists());
    }

    @Test
    @DisplayName("POST /api/orders: returns 400 when amount is zero")
    void createOrder_returns400WhenAmountIsZero() throws Exception {
        // Arrange — amount = 0 violates @DecimalMin("0.01")
        CreateOrderRequest request = new CreateOrderRequest(
                "c-1", "Item", BigDecimal.ZERO);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/orders: returns 400 when amount is null")
    void createOrder_returns400WhenAmountIsNull() throws Exception {
        // Arrange — amount is null, violates @NotNull
        CreateOrderRequest request = new CreateOrderRequest("c-1", "Item", null);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/orders
    // =========================================================================

    @Test
    @DisplayName("GET /api/orders: returns 200 with empty array when no orders exist")
    void getAllOrders_returns200WithEmptyArray() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/orders: returns all created orders")
    void getAllOrders_returnsCreatedOrders() throws Exception {
        // Arrange — create two orders first
        createOrderViaApi("c-1", "Item A", "10.00");
        createOrderViaApi("c-2", "Item B", "20.00");

        // Act & Assert — should return array with 2 elements
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // =========================================================================
    // GET /api/orders/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/orders/{id}: returns 200 with the order")
    void getOrder_returns200ForExistingOrder() throws Exception {
        // Arrange — create an order and capture its ID
        String orderId = createOrderAndGetId("c-1", "Monitor", "499.00");

        // Act & Assert
        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.customerId").value("c-1"));
    }

    @Test
    @DisplayName("GET /api/orders/{id}: returns 404 for unknown order ID")
    void getOrder_returns404ForUnknownId() throws Exception {
        // Act & Assert — no order with this ID exists
        mockMvc.perform(get("/api/orders/{id}", "non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // PATCH /api/orders/{id}/status
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/orders/{id}/status: updates order status to PROCESSING")
    void updateStatus_returns200WithUpdatedStatus() throws Exception {
        // Arrange
        String orderId = createOrderAndGetId("c-1", "Keyboard", "89.99");
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(Order.Status.PROCESSING);

        // Act & Assert
        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/status: returns 404 for unknown order")
    void updateStatus_returns404ForUnknownOrder() throws Exception {
        // Arrange
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(Order.Status.SHIPPED);

        // Act & Assert
        mockMvc.perform(patch("/api/orders/{id}/status", "ghost-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/orders/{id}/status: returns 400 when status is null")
    void updateStatus_returns400WhenStatusNull() throws Exception {
        // Arrange — create a valid order first
        String orderId = createOrderAndGetId("c-1", "Mouse", "29.99");
        // Body with null status field
        String body = "{\"status\": null}";

        // Act & Assert
        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // POST /api/orders/{id}/fail
    // =========================================================================

    @Test
    @DisplayName("POST /api/orders/{id}/fail: returns 200 and logs failure for existing order")
    void simulateFailure_returns200ForExistingOrder() throws Exception {
        // Arrange
        String orderId = createOrderAndGetId("c-1", "Camera", "799.00");

        // Act & Assert — endpoint just logs and returns OK
        mockMvc.perform(post("/api/orders/{id}/fail", orderId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/orders/{id}/fail: returns 404 for unknown order")
    void simulateFailure_returns404ForUnknownOrder() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/fail", "unknown-id"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates an order via the POST /api/orders endpoint and returns its ID.
     * Convenience method to reduce boilerplate in test arrange steps.
     *
     * @param customerId  customer identifier
     * @param description item description
     * @param amount      order amount as a decimal string
     * @return the generated order ID
     */
    private String createOrderAndGetId(String customerId, String description, String amount)
            throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                customerId, description, new BigDecimal(amount));

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract the ID from the response JSON
        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asText();
    }

    /**
     * Creates an order via the POST /api/orders endpoint (no return value).
     * Used when we only need to populate the store and don't need the ID.
     */
    private void createOrderViaApi(String customerId, String description, String amount)
            throws Exception {
        createOrderAndGetId(customerId, description, amount);
    }

    /**
     * Helper to assert the count of orders in the response.
     * Kept here for future use in more complex test scenarios.
     */
    @SuppressWarnings("unused")
    private void assertOrderCount(int expectedCount) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int actualCount = objectMapper.readTree(body).size();
        assertThat(actualCount).isEqualTo(expectedCount);
    }
}
