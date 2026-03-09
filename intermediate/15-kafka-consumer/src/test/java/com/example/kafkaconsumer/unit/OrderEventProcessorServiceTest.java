package com.example.kafkaconsumer.unit;

import com.example.kafkaconsumer.domain.OrderEvent;
import com.example.kafkaconsumer.domain.OrderStatus;
import com.example.kafkaconsumer.domain.ProcessedOrderEvent;
import com.example.kafkaconsumer.service.OrderEventProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderEventProcessorService}.
 *
 * <p>These tests exercise the service in complete isolation – no Spring context,
 * no Kafka broker, no Docker. The service is instantiated manually and tested
 * with plain Java objects. This makes the tests fast and deterministic.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>Happy path: a processed event is stored with the correct field values.</li>
 *   <li>Consumer metadata: partition and offset from the Kafka record are preserved.</li>
 *   <li>Multiple events: accumulated events are all retrievable in order.</li>
 *   <li>Status filtering: {@code getProcessedEventsByStatus()} returns only matching events.</li>
 *   <li>Count: {@code getProcessedCount()} reflects the actual number of stored events.</li>
 *   <li>Immutability: the list returned by {@code getProcessedEvents()} cannot be mutated.</li>
 * </ul>
 */
@DisplayName("OrderEventProcessorService – Unit Tests")
class OrderEventProcessorServiceTest {

    /** The service under test – created fresh before each test. */
    private OrderEventProcessorService service;

    @BeforeEach
    void setUp() {
        // Instantiate the service without Spring: no context needed for unit tests
        service = new OrderEventProcessorService();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a fully populated {@link OrderEvent} fixture.
     *
     * @param orderId the orderId to use in the fixture
     * @param status  the order status to use in the fixture
     * @return a new {@link OrderEvent} instance
     */
    private OrderEvent buildEvent(String orderId, OrderStatus status) {
        return new OrderEvent(
                UUID.randomUUID().toString(), // eventId
                orderId,
                "cust-42",
                "Widget Pro",
                2,
                new BigDecimal("19.98"),
                status,
                Instant.now()
        );
    }

    // ── process() – happy path ────────────────────────────────────────────────

    @Test
    @DisplayName("process() should store the event in the processed list")
    void process_shouldStoreEventInList() {
        OrderEvent event = buildEvent("order-1", OrderStatus.CREATED);

        service.process(event, 0, 0L);

        assertThat(service.getProcessedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("process() should preserve the eventId from the original event")
    void process_shouldPreserveEventId() {
        OrderEvent event = buildEvent("order-2", OrderStatus.CREATED);

        service.process(event, 0, 0L);

        ProcessedOrderEvent stored = service.getProcessedEvents().get(0);
        assertThat(stored.eventId()).isEqualTo(event.eventId());
    }

    @Test
    @DisplayName("process() should preserve the orderId from the original event")
    void process_shouldPreserveOrderId() {
        OrderEvent event = buildEvent("order-3", OrderStatus.CONFIRMED);

        service.process(event, 0, 0L);

        ProcessedOrderEvent stored = service.getProcessedEvents().get(0);
        assertThat(stored.orderId()).isEqualTo("order-3");
    }

    @Test
    @DisplayName("process() should preserve all original event fields")
    void process_shouldPreserveAllEventFields() {
        OrderEvent event = buildEvent("order-4", OrderStatus.SHIPPED);

        service.process(event, 1, 10L);

        ProcessedOrderEvent stored = service.getProcessedEvents().get(0);
        assertThat(stored.customerId()).isEqualTo(event.customerId());
        assertThat(stored.product()).isEqualTo(event.product());
        assertThat(stored.quantity()).isEqualTo(event.quantity());
        assertThat(stored.totalAmount()).isEqualByComparingTo(event.totalAmount());
        assertThat(stored.status()).isEqualTo(event.status());
        assertThat(stored.occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    @DisplayName("process() should store the Kafka partition in the ProcessedOrderEvent")
    void process_shouldStorePartition() {
        OrderEvent event = buildEvent("order-5", OrderStatus.CREATED);

        service.process(event, 2, 7L);

        ProcessedOrderEvent stored = service.getProcessedEvents().get(0);
        assertThat(stored.partition()).isEqualTo(2);
    }

    @Test
    @DisplayName("process() should store the Kafka offset in the ProcessedOrderEvent")
    void process_shouldStoreOffset() {
        OrderEvent event = buildEvent("order-6", OrderStatus.CREATED);

        service.process(event, 0, 42L);

        ProcessedOrderEvent stored = service.getProcessedEvents().get(0);
        assertThat(stored.offset()).isEqualTo(42L);
    }

    @Test
    @DisplayName("process() should set a non-null processedAt timestamp")
    void process_shouldSetProcessedAtTimestamp() {
        OrderEvent event = buildEvent("order-7", OrderStatus.CREATED);

        Instant before = Instant.now();
        service.process(event, 0, 0L);
        Instant after = Instant.now();

        ProcessedOrderEvent stored = service.getProcessedEvents().get(0);
        // processedAt must be between the timestamps captured around the call
        assertThat(stored.processedAt())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // ── process() – multiple events ───────────────────────────────────────────

    @Test
    @DisplayName("process() called multiple times should accumulate all events")
    void process_multipleEvents_shouldAccumulateAll() {
        service.process(buildEvent("order-a", OrderStatus.CREATED),  0, 0L);
        service.process(buildEvent("order-b", OrderStatus.CONFIRMED), 0, 1L);
        service.process(buildEvent("order-c", OrderStatus.SHIPPED),  1, 0L);

        assertThat(service.getProcessedEvents()).hasSize(3);
    }

    @Test
    @DisplayName("process() should preserve insertion order across multiple events")
    void process_multipleEvents_shouldPreserveOrder() {
        service.process(buildEvent("order-first",  OrderStatus.CREATED),   0, 0L);
        service.process(buildEvent("order-second", OrderStatus.CONFIRMED), 0, 1L);
        service.process(buildEvent("order-third",  OrderStatus.SHIPPED),   0, 2L);

        List<ProcessedOrderEvent> events = service.getProcessedEvents();
        assertThat(events.get(0).orderId()).isEqualTo("order-first");
        assertThat(events.get(1).orderId()).isEqualTo("order-second");
        assertThat(events.get(2).orderId()).isEqualTo("order-third");
    }

    // ── getProcessedCount() ───────────────────────────────────────────────────

    @Test
    @DisplayName("getProcessedCount() should return 0 when no events have been processed")
    void getProcessedCount_shouldReturnZeroInitially() {
        assertThat(service.getProcessedCount()).isZero();
    }

    @Test
    @DisplayName("getProcessedCount() should reflect the number of processed events")
    void getProcessedCount_shouldReflectStoredEvents() {
        service.process(buildEvent("order-c1", OrderStatus.CREATED),   0, 0L);
        service.process(buildEvent("order-c2", OrderStatus.CONFIRMED), 0, 1L);

        assertThat(service.getProcessedCount()).isEqualTo(2);
    }

    // ── getProcessedEventsByStatus() ──────────────────────────────────────────

    @Test
    @DisplayName("getProcessedEventsByStatus() should return only events with matching status")
    void getProcessedEventsByStatus_shouldFilterCorrectly() {
        service.process(buildEvent("order-s1", OrderStatus.CREATED),   0, 0L);
        service.process(buildEvent("order-s2", OrderStatus.CONFIRMED), 0, 1L);
        service.process(buildEvent("order-s3", OrderStatus.CREATED),   0, 2L);
        service.process(buildEvent("order-s4", OrderStatus.CANCELLED), 0, 3L);

        List<ProcessedOrderEvent> created = service.getProcessedEventsByStatus(OrderStatus.CREATED);
        assertThat(created).hasSize(2);
        assertThat(created).allMatch(e -> e.status() == OrderStatus.CREATED);
    }

    @Test
    @DisplayName("getProcessedEventsByStatus() should return empty list when no events match")
    void getProcessedEventsByStatus_shouldReturnEmptyWhenNoMatch() {
        service.process(buildEvent("order-d1", OrderStatus.CREATED), 0, 0L);

        List<ProcessedOrderEvent> delivered = service.getProcessedEventsByStatus(OrderStatus.DELIVERED);
        assertThat(delivered).isEmpty();
    }

    @Test
    @DisplayName("getProcessedEventsByStatus() should handle all status values")
    void getProcessedEventsByStatus_shouldHandleAllStatuses() {
        // Process one event of each status
        for (OrderStatus status : OrderStatus.values()) {
            service.process(buildEvent("order-" + status.name(), status), 0, 0L);
        }

        // Each status should return exactly one matching event
        for (OrderStatus status : OrderStatus.values()) {
            List<ProcessedOrderEvent> result = service.getProcessedEventsByStatus(status);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(status);
        }
    }

    // ── getProcessedEvents() – immutability ───────────────────────────────────

    @Test
    @DisplayName("getProcessedEvents() should return an unmodifiable list")
    void getProcessedEvents_shouldReturnUnmodifiableList() {
        service.process(buildEvent("order-imm", OrderStatus.CREATED), 0, 0L);

        List<ProcessedOrderEvent> events = service.getProcessedEvents();

        // Attempting to add to the returned list must throw UnsupportedOperationException
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> events.add(null)
        );
    }
}
