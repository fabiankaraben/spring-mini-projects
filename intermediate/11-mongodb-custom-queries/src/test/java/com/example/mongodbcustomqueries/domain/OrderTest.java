package com.example.mongodbcustomqueries.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Order} domain model.
 *
 * <p>These tests verify the domain model's constructor logic, getter/setter
 * behaviour, and any invariants that are enforced in the class itself.
 * No Spring context or MongoDB connection is required — these are pure Java tests.
 *
 * <p>Why test the domain model?
 * <ul>
 *   <li>Validates constructor logic (e.g. {@code createdAt} is set automatically).</li>
 *   <li>Catches regressions if a field mapping is changed or a getter is broken.</li>
 *   <li>Documents the expected shape of the domain object for readers of the code.</li>
 * </ul>
 */
@DisplayName("Order domain model unit tests")
class OrderTest {

    // ── Constructor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructor sets all fields correctly and auto-sets createdAt")
    void constructor_setsAllFieldsAndCreatedAt() {
        // Given: data for a new order
        List<OrderItem> items = List.of(
                new OrderItem("Laptop", "electronics", 1, new BigDecimal("999.99"))
        );

        // Capture a timestamp just before construction to bound createdAt
        Instant before = Instant.now();

        // When: create a new Order via the convenience constructor
        Order order = new Order("Alice", "North", "PENDING",
                new BigDecimal("999.99"), items);

        Instant after = Instant.now();

        // Then: all fields are set correctly
        assertThat(order.getCustomerName()).isEqualTo("Alice");
        assertThat(order.getRegion()).isEqualTo("North");
        assertThat(order.getStatus()).isEqualTo("PENDING");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("999.99");
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getProductName()).isEqualTo("Laptop");

        // The id should be null until MongoDB assigns one on insert
        assertThat(order.getId()).isNull();

        // createdAt should have been set to roughly "now" by the constructor
        assertThat(order.getCreatedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("No-arg constructor creates an empty order with null fields")
    void noArgConstructor_createsEmptyOrder() {
        // When: create via no-arg constructor (used by Spring Data for deserialisation)
        Order order = new Order();

        // Then: all fields are null (Spring Data will populate them during mapping)
        assertThat(order.getId()).isNull();
        assertThat(order.getCustomerName()).isNull();
        assertThat(order.getRegion()).isNull();
        assertThat(order.getStatus()).isNull();
        assertThat(order.getTotalAmount()).isNull();
        assertThat(order.getItems()).isNull();
        assertThat(order.getCreatedAt()).isNull();
    }

    // ── Setters ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Setters update fields correctly")
    void setters_updateFieldsCorrectly() {
        // Given: an empty order
        Order order = new Order();

        // When: set all fields via setters
        order.setId("507f1f77bcf86cd799439011");
        order.setCustomerName("Bob");
        order.setRegion("South");
        order.setStatus("DELIVERED");
        order.setTotalAmount(new BigDecimal("249.99"));
        List<OrderItem> items = List.of(new OrderItem("Book", "books", 2, new BigDecimal("24.99")));
        order.setItems(items);
        Instant now = Instant.now();
        order.setCreatedAt(now);

        // Then: getters return the updated values
        assertThat(order.getId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(order.getCustomerName()).isEqualTo("Bob");
        assertThat(order.getRegion()).isEqualTo("South");
        assertThat(order.getStatus()).isEqualTo("DELIVERED");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("249.99");
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getCreatedAt()).isEqualTo(now);
    }

    // ── toString ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes id, customerName, region, status, totalAmount and itemCount")
    void toString_includesKeyFields() {
        // Given: a fully-populated order
        Order order = new Order("Charlie", "East", "SHIPPED",
                new BigDecimal("150.00"),
                List.of(new OrderItem("Widget", "misc", 3, new BigDecimal("50.00"))));
        order.setId("abc123");

        // When
        String result = order.toString();

        // Then: the string representation contains key identifying information
        assertThat(result).contains("abc123");
        assertThat(result).contains("Charlie");
        assertThat(result).contains("East");
        assertThat(result).contains("SHIPPED");
        assertThat(result).contains("150.00");
    }
}
