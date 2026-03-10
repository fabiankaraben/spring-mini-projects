package com.example.activemqjms.service;

import com.example.activemqjms.domain.OrderMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderProcessingService}.
 *
 * <p>These tests verify the domain processing logic in complete isolation:
 * no Spring context, no JMS broker, no network — just the class under test
 * and plain JUnit 5 assertions.
 *
 * <h2>Testing strategy</h2>
 * <ul>
 *   <li>Instantiate {@link OrderProcessingService} directly with {@code new}.</li>
 *   <li>Call {@link OrderProcessingService#processOrder} with hand-crafted
 *       {@link OrderMessage} objects.</li>
 *   <li>Assert state changes via the public read methods.</li>
 * </ul>
 *
 * <h2>Why no mocks here?</h2>
 * <p>{@link OrderProcessingService} has no external dependencies — it only
 * manages an in-memory list. Mocking is therefore unnecessary; direct
 * instantiation is simpler and faster.
 */
@DisplayName("OrderProcessingService unit tests")
class OrderProcessingServiceTest {

    /**
     * The class under test, freshly created before each test to ensure isolation.
     */
    private OrderProcessingService orderProcessingService;

    /**
     * Create a fresh instance before each test method to prevent state leakage
     * between tests (the service stores an in-memory list of processed orders).
     */
    @BeforeEach
    void setUp() {
        orderProcessingService = new OrderProcessingService();
    }

    // ── processOrder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processOrder adds the message to the processed orders list")
    void processOrder_addsMessageToProcessedList() {
        // Given: an order message to process
        OrderMessage message = new OrderMessage("ORD-001", "Laptop", 2);

        // When: the order is processed
        orderProcessingService.processOrder(message);

        // Then: the processed list contains exactly one item
        List<OrderMessage> processed = orderProcessingService.getProcessedOrders();
        assertThat(processed).hasSize(1);

        // And: the item is the message we just processed
        assertThat(processed.get(0).getOrderId()).isEqualTo("ORD-001");
        assertThat(processed.get(0).getProduct()).isEqualTo("Laptop");
        assertThat(processed.get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("processOrder accumulates multiple messages in order")
    void processOrder_accumulatesMultipleMessages() {
        // Given: three different order messages
        OrderMessage msg1 = new OrderMessage("ORD-001", "Laptop", 1);
        OrderMessage msg2 = new OrderMessage("ORD-002", "Mouse", 3);
        OrderMessage msg3 = new OrderMessage("ORD-003", "Keyboard", 5);

        // When: all three are processed
        orderProcessingService.processOrder(msg1);
        orderProcessingService.processOrder(msg2);
        orderProcessingService.processOrder(msg3);

        // Then: all three appear in the processed list in insertion order
        List<OrderMessage> processed = orderProcessingService.getProcessedOrders();
        assertThat(processed).hasSize(3);
        assertThat(processed.get(0).getOrderId()).isEqualTo("ORD-001");
        assertThat(processed.get(1).getOrderId()).isEqualTo("ORD-002");
        assertThat(processed.get(2).getOrderId()).isEqualTo("ORD-003");
    }

    // ── getProcessedOrderCount ───────────────────────────────────────────────────

    @Test
    @DisplayName("getProcessedOrderCount returns 0 when no orders have been processed")
    void getProcessedOrderCount_returnsZero_whenEmpty() {
        // Given: a fresh service instance (no orders processed)

        // When / Then: count is 0
        assertThat(orderProcessingService.getProcessedOrderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("getProcessedOrderCount increments with each processed order")
    void getProcessedOrderCount_incrementsOnEachProcess() {
        // Given: a fresh service
        assertThat(orderProcessingService.getProcessedOrderCount()).isEqualTo(0);

        // When: process one order
        orderProcessingService.processOrder(new OrderMessage("ORD-A", "Widget", 1));

        // Then: count is 1
        assertThat(orderProcessingService.getProcessedOrderCount()).isEqualTo(1);

        // When: process another order
        orderProcessingService.processOrder(new OrderMessage("ORD-B", "Gadget", 2));

        // Then: count is 2
        assertThat(orderProcessingService.getProcessedOrderCount()).isEqualTo(2);
    }

    // ── getProcessedOrders ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProcessedOrders returns empty list when no orders processed")
    void getProcessedOrders_returnsEmptyList_whenNoOrdersProcessed() {
        // Given: a fresh service with no processed orders

        // When / Then: the returned list is empty (not null)
        assertThat(orderProcessingService.getProcessedOrders())
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("getProcessedOrders returns an unmodifiable snapshot")
    void getProcessedOrders_returnsUnmodifiableSnapshot() {
        // Given: one processed order
        orderProcessingService.processOrder(new OrderMessage("ORD-SNAP", "Camera", 1));

        // When: retrieve the list
        List<OrderMessage> snapshot = orderProcessingService.getProcessedOrders();

        // Then: attempting to modify the returned list throws an exception
        // This ensures callers cannot mutate the service's internal state
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new OrderMessage("ORD-HACK", "Injected", 99)));
    }

    @Test
    @DisplayName("processOrder preserves all fields of the OrderMessage")
    void processOrder_preservesAllFields() {
        // Given: a fully-populated order message
        OrderMessage original = new OrderMessage("ORD-FULL", "Premium Headphones", 7);
        String expectedMessageId = original.getMessageId();

        // When: process the message
        orderProcessingService.processOrder(original);

        // Then: the stored message has all fields intact
        OrderMessage stored = orderProcessingService.getProcessedOrders().get(0);
        assertThat(stored.getMessageId()).isEqualTo(expectedMessageId);
        assertThat(stored.getOrderId()).isEqualTo("ORD-FULL");
        assertThat(stored.getProduct()).isEqualTo("Premium Headphones");
        assertThat(stored.getQuantity()).isEqualTo(7);
        assertThat(stored.getCreatedAt()).isNotNull();
    }
}
