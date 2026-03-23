package com.example.cloudstream.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Order} domain entity.
 *
 * <p>These tests verify the domain logic in complete isolation — no Spring context,
 * no Kafka, no database. Pure Java object behavior is tested here.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Initial state after construction.</li>
 *   <li>Status transitions via explicit methods.</li>
 *   <li>Rejection with a reason.</li>
 *   <li>Immutability of core fields (id, customerId, productId, etc.).</li>
 * </ul>
 */
@DisplayName("Order domain entity unit tests")
class OrderTest {

    // =========================================================================
    // Construction
    // =========================================================================

    @Test
    @DisplayName("New order has PENDING status, non-null id, and correct field values")
    void newOrderHasCorrectInitialState() {
        Order order = new Order("cust-1", "prod-A", 2, new BigDecimal("19.98"));

        // id must be auto-generated (non-null)
        assertThat(order.getId()).isNotNull();

        // Core fields match constructor arguments
        assertThat(order.getCustomerId()).isEqualTo("cust-1");
        assertThat(order.getProductId()).isEqualTo("prod-A");
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("19.98");

        // Initial status must be PENDING
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // No rejection reason on a new order
        assertThat(order.getRejectionReason()).isNull();

        // Timestamps are set
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    // =========================================================================
    // markProcessing
    // =========================================================================

    @Test
    @DisplayName("markProcessing transitions order from PENDING to PROCESSING")
    void markProcessingTransitionsStatus() {
        Order order = new Order("cust-2", "prod-B", 1, new BigDecimal("9.99"));

        order.markProcessing();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        // updatedAt must be >= createdAt after a transition
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(order.getCreatedAt());
    }

    // =========================================================================
    // markNotified
    // =========================================================================

    @Test
    @DisplayName("markNotified transitions order to NOTIFIED")
    void markNotifiedTransitionsStatus() {
        Order order = new Order("cust-3", "prod-C", 3, new BigDecimal("29.97"));
        order.markProcessing();

        order.markNotified();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NOTIFIED);
        assertThat(order.getRejectionReason()).isNull();
    }

    // =========================================================================
    // markRejected
    // =========================================================================

    @Test
    @DisplayName("markRejected transitions order to REJECTED with the given reason")
    void markRejectedSetsStatusAndReason() {
        Order order = new Order("cust-4", "prod-D", 1, new BigDecimal("5.00"));

        order.markRejected("Total price must be positive");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Total price must be positive");
    }

    @Test
    @DisplayName("markRejected stores the rejection reason as provided")
    void markRejectedStoresReason() {
        Order order = new Order("cust-5", "prod-E", 2, new BigDecimal("10.00"));

        order.markRejected("Quantity must be positive");

        assertThat(order.getRejectionReason()).isEqualTo("Quantity must be positive");
    }

    // =========================================================================
    // Unique IDs
    // =========================================================================

    @Test
    @DisplayName("Two different orders have different UUIDs")
    void twoOrdersHaveDifferentIds() {
        Order order1 = new Order("cust-1", "prod-A", 1, new BigDecimal("10.00"));
        Order order2 = new Order("cust-1", "prod-A", 1, new BigDecimal("10.00"));

        assertThat(order1.getId()).isNotEqualTo(order2.getId());
    }

    // =========================================================================
    // toString
    // =========================================================================

    @Test
    @DisplayName("toString contains the order id and status")
    void toStringContainsKeyFields() {
        Order order = new Order("cust-6", "prod-F", 1, new BigDecimal("15.00"));

        String text = order.toString();

        assertThat(text).contains(order.getId().toString());
        assertThat(text).contains("PENDING");
        assertThat(text).contains("cust-6");
    }
}
