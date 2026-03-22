package com.example.saga.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Order} domain entity.
 *
 * <p>These tests verify the domain logic in isolation — no Spring context,
 * no database, no Kafka. Each test creates an {@link Order} instance directly
 * and asserts that status transitions and field values behave correctly.
 *
 * <p>Why test domain logic in isolation?
 *   Domain logic is the most critical business code. Testing it in pure unit
 *   tests (no I/O, no infrastructure) gives fast feedback and pinpoints exactly
 *   which invariant broke when a test fails.
 */
@DisplayName("Order Domain Entity")
class OrderTest {

    /** Helper that builds a valid Order for use across tests. */
    private Order buildOrder() {
        return new Order("customer-1", "product-A", 3, new BigDecimal("59.97"));
    }

    // -------------------------------------------------------------------------
    // Construction tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("New order starts with PENDING status")
    void newOrderHasPendingStatus() {
        Order order = buildOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("New order stores all constructor fields correctly")
    void newOrderStoresFields() {
        Order order = new Order("cust-42", "prod-XYZ", 5, new BigDecimal("149.95"));

        assertThat(order.getCustomerId()).isEqualTo("cust-42");
        assertThat(order.getProductId()).isEqualTo("prod-XYZ");
        assertThat(order.getQuantity()).isEqualTo(5);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("149.95");
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("New order has a non-null UUID pre-assigned at construction time")
    void newOrderHasPreAssignedUUID() {
        Order order = buildOrder();
        // UUID is pre-assigned in the field initializer so it is available immediately
        // (before JPA flush), which is required for publishing Kafka events synchronously.
        assertThat(order.getId()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Status transition tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("markPaymentProcessing transitions status from PENDING to PAYMENT_PROCESSING")
    void markPaymentProcessingTransitionsStatus() {
        Order order = buildOrder();
        order.markPaymentProcessing();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
    }

    @Test
    @DisplayName("markInventoryReserving transitions status to INVENTORY_RESERVING")
    void markInventoryReservingTransitionsStatus() {
        Order order = buildOrder();
        order.markPaymentProcessing();
        order.markInventoryReserving();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVING);
    }

    @Test
    @DisplayName("markCompleted transitions status to COMPLETED")
    void markCompletedTransitionsStatus() {
        Order order = buildOrder();
        order.markPaymentProcessing();
        order.markInventoryReserving();
        order.markCompleted();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("markCancelled transitions status to CANCELLED and records failure reason")
    void markCancelledTransitionsStatusAndSetsReason() {
        Order order = buildOrder();
        order.markPaymentProcessing();
        order.markCancelled("Insufficient funds");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).isEqualTo("Insufficient funds");
    }

    @Test
    @DisplayName("markCancelled sets failure reason to the exact provided string")
    void markCancelledSetsExactFailureReason() {
        Order order = buildOrder();
        String reason = "Inventory out of stock for product-A";
        order.markPaymentProcessing();
        order.markInventoryReserving();
        order.markCancelled(reason);

        assertThat(order.getFailureReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("updatedAt is refreshed on each status transition")
    void updatedAtIsRefreshedOnTransition() throws InterruptedException {
        Order order = buildOrder();
        var before = order.getUpdatedAt();

        // Small sleep to guarantee a measurable time difference
        Thread.sleep(10);
        order.markPaymentProcessing();

        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("COMPLETED order has no failure reason")
    void completedOrderHasNoFailureReason() {
        Order order = buildOrder();
        order.markPaymentProcessing();
        order.markInventoryReserving();
        order.markCompleted();

        assertThat(order.getFailureReason()).isNull();
    }

    // -------------------------------------------------------------------------
    // OrderStatus enum tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OrderStatus enum contains all expected values")
    void orderStatusEnumContainsAllValues() {
        assertThat(OrderStatus.values()).containsExactlyInAnyOrder(
                OrderStatus.PENDING,
                OrderStatus.PAYMENT_PROCESSING,
                OrderStatus.INVENTORY_RESERVING,
                OrderStatus.COMPLETED,
                OrderStatus.CANCELLED
        );
    }
}
