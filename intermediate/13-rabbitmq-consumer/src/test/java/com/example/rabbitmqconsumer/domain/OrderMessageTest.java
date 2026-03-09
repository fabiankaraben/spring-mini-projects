package com.example.rabbitmqconsumer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link OrderMessage} domain model.
 *
 * <p>These tests verify the domain object's construction and field mapping
 * without loading any Spring context. They run in milliseconds because no
 * infrastructure (RabbitMQ, HTTP server, etc.) is involved.
 *
 * <p>Testing the domain model in isolation ensures that:
 * <ul>
 *   <li>The all-args constructor correctly maps all provided arguments.</li>
 *   <li>The no-arg constructor (needed by Jackson) leaves fields at their
 *       default values so Jackson can populate them via setters.</li>
 *   <li>Setters and getters form a correct round-trip.</li>
 *   <li>{@code toString()} includes key identifiers for log readability.</li>
 * </ul>
 */
@DisplayName("OrderMessage domain model unit tests")
class OrderMessageTest {

    // ── All-args constructor tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("All-args constructor maps all fields correctly")
    void allArgsConstructor_mapsFieldsCorrectly() {
        // Given: a fixed timestamp and a message ID
        Instant now = Instant.parse("2024-01-15T10:00:00Z");

        // When: create an OrderMessage using the all-args constructor
        OrderMessage message = new OrderMessage("msg-uuid-1", "ORD-001", "Laptop", 3, now);

        // Then: every field must be stored exactly as provided
        assertThat(message.getMessageId()).isEqualTo("msg-uuid-1");
        assertThat(message.getOrderId()).isEqualTo("ORD-001");
        assertThat(message.getProduct()).isEqualTo("Laptop");
        assertThat(message.getQuantity()).isEqualTo(3);
        assertThat(message.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("All-args constructor accepts quantity of 1 (minimum valid value)")
    void allArgsConstructor_acceptsMinimumQuantity() {
        // When
        OrderMessage message = new OrderMessage("id", "ORD-MIN", "USB Cable", 1, Instant.now());

        // Then
        assertThat(message.getQuantity()).isEqualTo(1);
    }

    // ── No-arg constructor (required by Jackson) ──────────────────────────────────

    @Test
    @DisplayName("No-arg constructor creates an instance with null/zero fields (Jackson compatibility)")
    void noArgConstructor_createsInstanceWithDefaultFields() {
        // When: create via the no-arg constructor (as Jackson does during JSON deserialisation)
        OrderMessage message = new OrderMessage();

        // Then: reference fields are null, primitive int defaults to 0
        assertThat(message.getMessageId()).isNull();
        assertThat(message.getOrderId()).isNull();
        assertThat(message.getProduct()).isNull();
        assertThat(message.getQuantity()).isZero();
        assertThat(message.getCreatedAt()).isNull();
    }

    // ── Setter / Getter round-trip ────────────────────────────────────────────────

    @Test
    @DisplayName("Setters update the fields and getters return the updated values")
    void settersAndGetters_roundTrip() {
        // Given: an empty OrderMessage (as Jackson creates it)
        OrderMessage message = new OrderMessage();
        Instant ts = Instant.parse("2024-06-01T12:00:00Z");

        // When: populate via setters (simulating Jackson JSON deserialisation)
        message.setMessageId("test-uuid");
        message.setOrderId("ORD-999");
        message.setProduct("Webcam");
        message.setQuantity(7);
        message.setCreatedAt(ts);

        // Then: getters must return the exact set values
        assertThat(message.getMessageId()).isEqualTo("test-uuid");
        assertThat(message.getOrderId()).isEqualTo("ORD-999");
        assertThat(message.getProduct()).isEqualTo("Webcam");
        assertThat(message.getQuantity()).isEqualTo(7);
        assertThat(message.getCreatedAt()).isEqualTo(ts);
    }

    @Test
    @DisplayName("setQuantity accepts large positive values")
    void setQuantity_acceptsLargePositiveValue() {
        // When
        OrderMessage message = new OrderMessage();
        message.setQuantity(Integer.MAX_VALUE);

        // Then
        assertThat(message.getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    // ── toString ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes orderId, product, and quantity for readable log output")
    void toString_includesKeyFields() {
        // When
        OrderMessage message = new OrderMessage("id-123", "ORD-LOG", "Speaker", 4, Instant.now());

        // Then: toString must contain the key identifiers used in log analysis
        String text = message.toString();
        assertThat(text).contains("ORD-LOG");
        assertThat(text).contains("Speaker");
        assertThat(text).contains("4");
    }

    @Test
    @DisplayName("toString includes messageId")
    void toString_includesMessageId() {
        // When
        OrderMessage message = new OrderMessage("uuid-abc", "ORD-1", "Product", 1, Instant.now());

        // Then
        assertThat(message.toString()).contains("uuid-abc");
    }
}
