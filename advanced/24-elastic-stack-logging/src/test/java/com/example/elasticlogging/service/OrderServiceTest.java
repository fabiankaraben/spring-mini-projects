package com.example.elasticlogging.service;

import com.example.elasticlogging.dto.CreateOrderRequest;
import com.example.elasticlogging.exception.OrderNotFoundException;
import com.example.elasticlogging.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>These tests exercise pure domain logic in isolation — no Spring context,
 * no Docker containers, no network I/O. They run fast and give immediate
 * feedback on business rule correctness.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>Order creation — correct initial state</li>
 *   <li>Order retrieval — happy path and not-found case</li>
 *   <li>Status update — transition correctness and not-found case</li>
 *   <li>Processing failure simulation — error-level log path</li>
 *   <li>Collection listing — correct count</li>
 * </ul>
 *
 * <h2>Logging note</h2>
 * These tests indirectly exercise the structured logging code paths (the
 * {@code log.info/warn/error} calls in OrderService). Although we do not
 * assert on log output here (that is tested in the integration tests via
 * log file inspection), verifying the service methods don't throw helps
 * confirm the logging calls are syntactically and semantically correct.
 */
@DisplayName("OrderService unit tests")
class OrderServiceTest {

    /** Subject under test — instantiated directly, no Spring context needed. */
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Create a fresh service instance for each test to avoid state leakage
        // between test cases (each test gets an empty orderStore).
        orderService = new OrderService();
    }

    // =========================================================================
    // createOrder
    // =========================================================================

    @Test
    @DisplayName("createOrder: returns order with correct initial state")
    void createOrder_returnsOrderWithCorrectInitialState() {
        // Arrange
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-001", "Laptop model X", new BigDecimal("1299.99"));

        // Act
        Order order = orderService.createOrder(request);

        // Assert — verify every field is set correctly on creation
        assertThat(order.getId()).isNotBlank();
        assertThat(order.getCustomerId()).isEqualTo("customer-001");
        assertThat(order.getDescription()).isEqualTo("Laptop model X");
        assertThat(order.getAmount()).isEqualByComparingTo("1299.99");
        // Every new order must start in PENDING status
        assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING);
        assertThat(order.getCreatedAt()).isNotNull();
        // updatedAt must be null — it is only set on status changes
        assertThat(order.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("createOrder: increments the order count")
    void createOrder_incrementsOrderCount() {
        // Arrange — no orders exist initially
        assertThat(orderService.getOrderCount()).isZero();

        // Act
        orderService.createOrder(new CreateOrderRequest("c-1", "Item A", BigDecimal.TEN));
        orderService.createOrder(new CreateOrderRequest("c-2", "Item B", BigDecimal.ONE));

        // Assert
        assertThat(orderService.getOrderCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("createOrder: each order gets a unique ID")
    void createOrder_eachOrderGetsUniqueId() {
        // Arrange & Act — create two orders with identical data
        CreateOrderRequest request = new CreateOrderRequest("c-1", "Item", BigDecimal.TEN);
        Order order1 = orderService.createOrder(request);
        Order order2 = orderService.createOrder(request);

        // Assert — IDs must be different (generated from UUID.randomUUID())
        assertThat(order1.getId()).isNotEqualTo(order2.getId());
    }

    // =========================================================================
    // getOrder
    // =========================================================================

    @Test
    @DisplayName("getOrder: retrieves an existing order by ID")
    void getOrder_returnsExistingOrder() {
        // Arrange
        Order created = orderService.createOrder(
                new CreateOrderRequest("c-1", "Monitor", new BigDecimal("499.00")));

        // Act
        Order retrieved = orderService.getOrder(created.getId());

        // Assert — same instance (same ID, same fields)
        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getCustomerId()).isEqualTo("c-1");
    }

    @Test
    @DisplayName("getOrder: throws OrderNotFoundException for unknown ID")
    void getOrder_throwsForUnknownId() {
        // Act & Assert — calling getOrder with a non-existent ID must throw
        assertThatThrownBy(() -> orderService.getOrder("non-existent-id"))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("non-existent-id");
    }

    @Test
    @DisplayName("getOrder: OrderNotFoundException carries the missing order ID")
    void getOrder_exceptionCarriesOrderId() {
        // Act
        OrderNotFoundException ex = catchThrowableOfType(
                () -> orderService.getOrder("missing-id"),
                OrderNotFoundException.class
        );

        // Assert — the exception must expose the ID for structured logging
        assertThat(ex).isNotNull();
        assertThat(ex.getOrderId()).isEqualTo("missing-id");
    }

    // =========================================================================
    // getAllOrders
    // =========================================================================

    @Test
    @DisplayName("getAllOrders: returns empty collection initially")
    void getAllOrders_emptyInitially() {
        assertThat(orderService.getAllOrders()).isEmpty();
    }

    @Test
    @DisplayName("getAllOrders: returns all created orders")
    void getAllOrders_returnsAllCreatedOrders() {
        // Arrange
        orderService.createOrder(new CreateOrderRequest("c-1", "A", BigDecimal.TEN));
        orderService.createOrder(new CreateOrderRequest("c-2", "B", BigDecimal.ONE));
        orderService.createOrder(new CreateOrderRequest("c-3", "C", new BigDecimal("5.00")));

        // Act & Assert
        assertThat(orderService.getAllOrders()).hasSize(3);
    }

    // =========================================================================
    // updateOrderStatus
    // =========================================================================

    @Test
    @DisplayName("updateOrderStatus: transitions order from PENDING to PROCESSING")
    void updateOrderStatus_pendingToProcessing() {
        // Arrange
        Order order = orderService.createOrder(
                new CreateOrderRequest("c-1", "Widget", BigDecimal.TEN));

        // Act
        Order updated = orderService.updateOrderStatus(order.getId(), Order.Status.PROCESSING);

        // Assert
        assertThat(updated.getStatus()).isEqualTo(Order.Status.PROCESSING);
        // updatedAt must be set after a status change
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateOrderStatus: transitions order to SHIPPED")
    void updateOrderStatus_toShipped() {
        // Arrange — create and advance to PROCESSING first
        Order order = orderService.createOrder(
                new CreateOrderRequest("c-1", "Widget", BigDecimal.TEN));
        orderService.updateOrderStatus(order.getId(), Order.Status.PROCESSING);

        // Act
        Order updated = orderService.updateOrderStatus(order.getId(), Order.Status.SHIPPED);

        // Assert
        assertThat(updated.getStatus()).isEqualTo(Order.Status.SHIPPED);
    }

    @Test
    @DisplayName("updateOrderStatus: transitions order to CANCELLED")
    void updateOrderStatus_toCancelled() {
        // Arrange
        Order order = orderService.createOrder(
                new CreateOrderRequest("c-1", "Widget", BigDecimal.TEN));

        // Act
        Order updated = orderService.updateOrderStatus(order.getId(), Order.Status.CANCELLED);

        // Assert
        assertThat(updated.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateOrderStatus: throws OrderNotFoundException for unknown ID")
    void updateOrderStatus_throwsForUnknownId() {
        // Act & Assert
        assertThatThrownBy(() ->
                orderService.updateOrderStatus("unknown-id", Order.Status.SHIPPED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // =========================================================================
    // simulateProcessingFailure
    // =========================================================================

    @Test
    @DisplayName("simulateProcessingFailure: completes without throwing for existing order")
    void simulateProcessingFailure_doesNotThrowForExistingOrder() {
        // Arrange
        Order order = orderService.createOrder(
                new CreateOrderRequest("c-1", "Gadget", new BigDecimal("25.00")));

        // Act & Assert — method must not throw; the failure is only logged
        assertThatNoException().isThrownBy(
                () -> orderService.simulateProcessingFailure(order.getId()));
    }

    @Test
    @DisplayName("simulateProcessingFailure: throws OrderNotFoundException for unknown ID")
    void simulateProcessingFailure_throwsForUnknownId() {
        // Act & Assert
        assertThatThrownBy(() -> orderService.simulateProcessingFailure("ghost-id"))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
