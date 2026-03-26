package com.example.camundabpm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Order} domain entity.
 *
 * <p>These are pure unit tests — no Spring context, no database, no Camunda engine.
 * They verify the entity's constructor, default values, and state mutation methods
 * in isolation, running entirely in-memory.
 *
 * <p>Why test the domain entity?
 * Domain entities encapsulate business invariants. Testing them directly:
 *   - Verifies constructors set the correct initial state.
 *   - Verifies that status transitions update the updatedAt timestamp.
 *   - Catches regressions if the entity is refactored.
 *   - Runs in milliseconds (no infrastructure setup).
 */
@DisplayName("Order domain entity")
class OrderTest {

    /**
     * Tests that the Order constructor creates an entity with the correct
     * initial field values and status PENDING.
     */
    @Test
    @DisplayName("constructor sets all fields correctly and status to PENDING")
    void constructor_setsAllFieldsAndPendingStatus() {
        // Arrange
        String customerName = "Alice";
        String productName = "Laptop Pro 15";
        Integer quantity = 2;
        BigDecimal unitPrice = new BigDecimal("1299.99");

        // Act: record the time just before construction to bound createdAt
        Instant before = Instant.now();
        Order order = new Order(customerName, productName, quantity, unitPrice);
        Instant after = Instant.now();

        // Assert: all constructor arguments are stored correctly
        assertThat(order.getCustomerName()).isEqualTo(customerName);
        assertThat(order.getProductName()).isEqualTo(productName);
        assertThat(order.getQuantity()).isEqualTo(quantity);
        assertThat(order.getUnitPrice()).isEqualByComparingTo(unitPrice);

        // Assert: initial status must be PENDING
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Assert: id and totalAmount are null before persistence
        assertThat(order.getId()).isNull();
        assertThat(order.getTotalAmount()).isNull();
        assertThat(order.getProcessInstanceId()).isNull();
        assertThat(order.getTrackingNumber()).isNull();
        assertThat(order.getErrorMessage()).isNull();

        // Assert: timestamps are within the expected window
        assertThat(order.getCreatedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
        assertThat(order.getUpdatedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    /**
     * Tests that calling setStatus() updates both the status and the updatedAt timestamp.
     */
    @Test
    @DisplayName("setStatus updates status and refreshes updatedAt")
    void setStatus_updatesStatusAndUpdatedAt() throws InterruptedException {
        // Arrange
        Order order = new Order("Bob", "USB-C Hub", 1, new BigDecimal("49.99"));
        Instant originalUpdatedAt = order.getUpdatedAt();

        // Wait 1ms to ensure the new updatedAt is strictly after the original
        Thread.sleep(1);

        // Act
        order.setStatus(OrderStatus.INVENTORY_CHECKED);

        // Assert: status changed
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_CHECKED);

        // Assert: updatedAt was refreshed (strictly after the original value)
        assertThat(order.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    /**
     * Tests the full happy-path status progression through all states.
     */
    @Test
    @DisplayName("supports full status lifecycle from PENDING to COMPLETED")
    void statusLifecycle_happyPath() {
        // Arrange
        Order order = new Order("Charlie", "Mechanical Keyboard", 1, new BigDecimal("149.99"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Act & Assert: step through each status in sequence
        order.setStatus(OrderStatus.INVENTORY_CHECKED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_CHECKED);

        order.setStatus(OrderStatus.PAYMENT_PROCESSED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSED);

        order.setStatus(OrderStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        order.setStatus(OrderStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    /**
     * Tests the failure path — any step can transition to FAILED with an error message.
     */
    @Test
    @DisplayName("can be set to FAILED status with an error message")
    void setStatus_failedWithErrorMessage() {
        // Arrange
        Order order = new Order("Diana", "Wireless Mouse", 0, new BigDecimal("29.99"));
        order.setStatus(OrderStatus.FAILED);
        order.setErrorMessage("Insufficient inventory for quantity: 0");

        // Assert
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getErrorMessage()).isEqualTo("Insufficient inventory for quantity: 0");
    }

    /**
     * Tests that setTrackingNumber and setTotalAmount persist the provided values.
     */
    @Test
    @DisplayName("setters for trackingNumber and totalAmount work correctly")
    void setters_trackingNumberAndTotalAmount() {
        // Arrange
        Order order = new Order("Eve", "Monitor 4K", 1, new BigDecimal("599.99"));

        // Act
        order.setTotalAmount(new BigDecimal("599.99"));
        order.setTrackingNumber("TRK-ABC123");
        order.setProcessInstanceId("proc-instance-001");

        // Assert
        assertThat(order.getTotalAmount()).isEqualByComparingTo("599.99");
        assertThat(order.getTrackingNumber()).isEqualTo("TRK-ABC123");
        assertThat(order.getProcessInstanceId()).isEqualTo("proc-instance-001");
    }

    /**
     * Tests that createdAt is immutable — it must not change after construction.
     */
    @Test
    @DisplayName("createdAt is set at construction and does not change on status update")
    void createdAt_isImmutable() throws InterruptedException {
        // Arrange
        Order order = new Order("Frank", "Headphones", 1, new BigDecimal("199.99"));
        Instant createdAt = order.getCreatedAt();

        Thread.sleep(1);

        // Act: update status (which refreshes updatedAt)
        order.setStatus(OrderStatus.INVENTORY_CHECKED);

        // Assert: createdAt has not changed
        assertThat(order.getCreatedAt()).isEqualTo(createdAt);
    }
}
