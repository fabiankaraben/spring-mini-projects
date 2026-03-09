package com.example.kafkaproducer.unit;

import com.example.kafkaproducer.domain.OrderEvent;
import com.example.kafkaproducer.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link OrderEvent} domain record.
 *
 * <p>These tests verify the core domain logic in complete isolation:
 * no Spring context, no Kafka broker, no Docker containers. They run
 * entirely in-memory and should complete in milliseconds.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>The {@link OrderEvent#create} factory method auto-generates a
 *       non-null UUID {@code eventId}.</li>
 *   <li>The factory method sets {@code occurredAt} to a non-null
 *       {@link Instant} close to the current time.</li>
 *   <li>Business fields are correctly mapped from the factory parameters.</li>
 *   <li>Two calls to {@code create} produce different {@code eventId} values
 *       (UUID uniqueness).</li>
 *   <li>Records with the same field values are considered equal.</li>
 * </ul>
 */
@DisplayName("OrderEvent – Unit Tests")
class OrderEventTest {

    // ── Factory method tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("create() should generate a non-null, non-blank eventId")
    void create_shouldGenerateNonBlankEventId() {
        OrderEvent event = OrderEvent.create(
                "order-1", "cust-1", "Widget", 2,
                new BigDecimal("19.99"), OrderStatus.CREATED);

        // The factory must auto-assign a UUID string
        assertThat(event.eventId()).isNotNull().isNotBlank();
        // UUID format: 8-4-4-4-12 hex characters separated by hyphens
        assertThat(event.eventId()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("create() should set occurredAt to a recent Instant")
    void create_shouldSetOccurredAtToCurrentTime() {
        Instant before = Instant.now();
        OrderEvent event = OrderEvent.create(
                "order-1", "cust-1", "Widget", 2,
                new BigDecimal("19.99"), OrderStatus.CREATED);
        Instant after = Instant.now();

        // occurredAt must be between the timestamps captured before and after
        // the factory call, confirming it uses Instant.now() internally
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.occurredAt()).isAfterOrEqualTo(before);
        assertThat(event.occurredAt()).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("create() should map all business fields correctly")
    void create_shouldMapBusinessFieldsCorrectly() {
        BigDecimal amount = new BigDecimal("99.50");
        OrderEvent event = OrderEvent.create(
                "order-42", "cust-99", "Gadget Pro", 5,
                amount, OrderStatus.CONFIRMED);

        assertThat(event.orderId()).isEqualTo("order-42");
        assertThat(event.customerId()).isEqualTo("cust-99");
        assertThat(event.product()).isEqualTo("Gadget Pro");
        assertThat(event.quantity()).isEqualTo(5);
        assertThat(event.totalAmount()).isEqualByComparingTo(amount);
        assertThat(event.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("create() should produce unique eventIds on each call")
    void create_shouldProduceUniqueEventIds() {
        // Each call to create() must generate a different UUID
        OrderEvent event1 = OrderEvent.create(
                "order-1", "cust-1", "Widget", 1,
                new BigDecimal("10.00"), OrderStatus.CREATED);
        OrderEvent event2 = OrderEvent.create(
                "order-1", "cust-1", "Widget", 1,
                new BigDecimal("10.00"), OrderStatus.CREATED);

        assertThat(event1.eventId()).isNotEqualTo(event2.eventId());
    }

    // ── Record equality tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Two OrderEvent records with identical fields should be equal")
    void orderEvent_recordEqualityHoldsForIdenticalFields() {
        // Java records auto-generate equals() based on all component values
        String eventId = "fixed-event-id";
        Instant now = Instant.now();
        BigDecimal amount = new BigDecimal("50.00");

        OrderEvent event1 = new OrderEvent(eventId, "o1", "c1", "Prod", 3, amount, OrderStatus.SHIPPED, now);
        OrderEvent event2 = new OrderEvent(eventId, "o1", "c1", "Prod", 3, amount, OrderStatus.SHIPPED, now);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("Two OrderEvent records with different eventIds should not be equal")
    void orderEvent_recordsWithDifferentEventIdShouldNotBeEqual() {
        Instant now = Instant.now();
        BigDecimal amount = new BigDecimal("50.00");

        OrderEvent event1 = new OrderEvent("id-A", "o1", "c1", "Prod", 3, amount, OrderStatus.SHIPPED, now);
        OrderEvent event2 = new OrderEvent("id-B", "o1", "c1", "Prod", 3, amount, OrderStatus.SHIPPED, now);

        assertThat(event1).isNotEqualTo(event2);
    }

    // ── OrderStatus enum tests ────────────────────────────────────────────────

    @Test
    @DisplayName("All OrderStatus values should be accessible")
    void orderStatus_allValuesShouldBeAccessible() {
        // Verifies all five statuses are declared and accessible by name
        assertThat(OrderStatus.CREATED).isNotNull();
        assertThat(OrderStatus.CONFIRMED).isNotNull();
        assertThat(OrderStatus.SHIPPED).isNotNull();
        assertThat(OrderStatus.DELIVERED).isNotNull();
        assertThat(OrderStatus.CANCELLED).isNotNull();
    }
}
