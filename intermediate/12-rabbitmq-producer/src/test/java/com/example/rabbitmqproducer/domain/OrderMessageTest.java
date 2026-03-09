package com.example.rabbitmqproducer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link OrderMessage} domain model.
 *
 * <p>These tests verify the domain object's construction and auto-generated
 * field behaviour without loading any Spring context. They run in milliseconds
 * because no infrastructure (RabbitMQ, HTTP server, etc.) is involved.
 *
 * <p>Testing the domain model in isolation ensures that:
 * <ul>
 *   <li>The constructor correctly maps the provided arguments.</li>
 *   <li>Auto-generated fields ({@code messageId}, {@code createdAt}) are always
 *       populated with meaningful values.</li>
 *   <li>Each instance produces a unique {@code messageId} (UUID randomness).</li>
 *   <li>The no-arg constructor (needed by Jackson) leaves fields as {@code null},
 *       so Jackson can set them via setters during deserialisation.</li>
 * </ul>
 */
@DisplayName("OrderMessage domain model unit tests")
class OrderMessageTest {

    // ── Constructor tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full constructor maps orderId, product, and quantity correctly")
    void constructor_mapsFieldsCorrectly() {
        // When: create an OrderMessage using the full constructor
        OrderMessage message = new OrderMessage("ORD-001", "Laptop", 3);

        // Then: the provided fields are stored as-is
        assertThat(message.getOrderId()).isEqualTo("ORD-001");
        assertThat(message.getProduct()).isEqualTo("Laptop");
        assertThat(message.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Full constructor auto-generates a non-null messageId (UUID format)")
    void constructor_autoGeneratesMessageId() {
        // When: create an OrderMessage
        OrderMessage message = new OrderMessage("ORD-002", "Mouse", 1);

        // Then: messageId must be a non-blank UUID string
        assertThat(message.getMessageId())
                .isNotNull()
                .isNotBlank()
                // UUID format: 8-4-4-4-12 hex characters separated by hyphens
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Full constructor auto-generates a non-null createdAt timestamp")
    void constructor_autoGeneratesCreatedAt() {
        // Record the time just before creating the message
        Instant before = Instant.now();

        // When: create an OrderMessage
        OrderMessage message = new OrderMessage("ORD-003", "Keyboard", 2);

        // Record the time just after
        Instant after = Instant.now();

        // Then: createdAt must be within the [before, after] window
        assertThat(message.getCreatedAt())
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("Two distinct OrderMessage instances have different messageIds")
    void constructor_generatesDifferentMessageIdPerInstance() {
        // When: create two separate messages
        OrderMessage first = new OrderMessage("ORD-A", "Product A", 1);
        OrderMessage second = new OrderMessage("ORD-B", "Product B", 1);

        // Then: their messageIds must be different (UUID is random per instance)
        assertThat(first.getMessageId()).isNotEqualTo(second.getMessageId());
    }

    // ── No-arg constructor (required by Jackson) ──────────────────────────────────

    @Test
    @DisplayName("No-arg constructor creates an instance with null fields (Jackson compatibility)")
    void noArgConstructor_createsInstanceWithNullFields() {
        // When: create via the no-arg constructor (as Jackson does during deserialisation)
        OrderMessage message = new OrderMessage();

        // Then: all fields are null (Jackson will set them via setters)
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
        // Given: an empty OrderMessage
        OrderMessage message = new OrderMessage();
        Instant now = Instant.now();

        // When: populate via setters (simulating Jackson deserialisation)
        message.setMessageId("test-uuid");
        message.setOrderId("ORD-999");
        message.setProduct("Webcam");
        message.setQuantity(7);
        message.setCreatedAt(now);

        // Then: getters return the set values
        assertThat(message.getMessageId()).isEqualTo("test-uuid");
        assertThat(message.getOrderId()).isEqualTo("ORD-999");
        assertThat(message.getProduct()).isEqualTo("Webcam");
        assertThat(message.getQuantity()).isEqualTo(7);
        assertThat(message.getCreatedAt()).isEqualTo(now);
    }

    // ── toString ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes orderId and product for readable log output")
    void toString_includesKeyFields() {
        // When
        OrderMessage message = new OrderMessage("ORD-LOG", "Speaker", 4);

        // Then: toString must contain the key identifiers for log readability
        String text = message.toString();
        assertThat(text).contains("ORD-LOG");
        assertThat(text).contains("Speaker");
        assertThat(text).contains("4");
    }
}
