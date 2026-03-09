package com.example.kafkaconsumer.unit;

import com.example.kafkaconsumer.domain.OrderEvent;
import com.example.kafkaconsumer.domain.OrderStatus;
import com.example.kafkaconsumer.listener.OrderEventListener;
import com.example.kafkaconsumer.service.OrderEventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link OrderEventListener}.
 *
 * <p>These tests exercise the listener in complete isolation – no Spring context,
 * no Kafka broker. The {@link OrderEventProcessorService} dependency and the
 * {@link Acknowledgment} are replaced by Mockito mocks, allowing us to:
 * <ul>
 *   <li>Verify the listener delegates to the service with the correct arguments.</li>
 *   <li>Verify the offset is acknowledged after successful processing.</li>
 *   <li>Verify the offset is NOT acknowledged when processing throws an exception.</li>
 *   <li>Verify the listener re-throws on failure so the container can manage retries.</li>
 * </ul>
 *
 * <h2>Testing approach</h2>
 * <p>The listener method signature is:
 * <pre>
 *   void onOrderEvent(ConsumerRecord&lt;String, OrderEvent&gt; record, Acknowledgment ack)
 * </pre>
 * We build a real {@link ConsumerRecord} with a specific partition/offset so we can
 * assert the correct metadata is extracted and forwarded to the service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventListener – Unit Tests")
class OrderEventListenerTest {

    /**
     * Mock of the {@link OrderEventProcessor} interface – prevents actual business
     * logic from running and lets us verify interaction without any state side effects.
     *
     * <p>We mock the <em>interface</em> rather than the concrete
     * {@code OrderEventProcessorService} class because Mockito can always create a
     * JDK dynamic proxy for an interface without bytecode manipulation. Mocking
     * concrete classes requires the inline mock maker, which is blocked by the Java
     * module system on Java 21+ and fails entirely on Java 25.
     */
    @Mock
    private OrderEventProcessor processorService;

    /**
     * Mock of the Kafka {@link Acknowledgment} handle – lets us verify
     * whether {@code acknowledge()} was called or not.
     */
    @Mock
    private Acknowledgment acknowledgment;

    /** The listener under test. */
    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        // Instantiate the listener manually with the mocked service
        listener = new OrderEventListener(processorService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a {@link ConsumerRecord} carrying the given event, using the specified
     * partition and offset. This simulates what the Kafka container hands to the
     * listener method after deserialisation.
     */
    private ConsumerRecord<String, OrderEvent> buildRecord(
            OrderEvent event, int partition, long offset) {
        return new ConsumerRecord<>(
                "order-events",  // topic
                partition,
                offset,
                event.orderId(), // key
                event            // value
        );
    }

    /**
     * Creates a minimal {@link OrderEvent} fixture for use in tests.
     */
    private OrderEvent buildEvent(String orderId, OrderStatus status) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                orderId,
                "cust-test",
                "Widget",
                1,
                new BigDecimal("9.99"),
                status,
                Instant.now()
        );
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("onOrderEvent() should delegate to the processor service with the event value")
    void onOrderEvent_shouldDelegateToProcessorService() {
        OrderEvent event = buildEvent("order-1", OrderStatus.CREATED);
        ConsumerRecord<String, OrderEvent> record = buildRecord(event, 0, 5L);

        listener.onOrderEvent(record, acknowledgment);

        // Verify the service was called with the correct event from the record
        verify(processorService).process(event, 0, 5L);
    }

    @Test
    @DisplayName("onOrderEvent() should pass the correct partition to the processor service")
    void onOrderEvent_shouldPassCorrectPartition() {
        OrderEvent event = buildEvent("order-2", OrderStatus.CONFIRMED);
        ConsumerRecord<String, OrderEvent> record = buildRecord(event, 2, 0L);

        listener.onOrderEvent(record, acknowledgment);

        verify(processorService).process(any(OrderEvent.class), anyInt(), anyLong());
        verify(processorService).process(event, 2, 0L);
    }

    @Test
    @DisplayName("onOrderEvent() should pass the correct offset to the processor service")
    void onOrderEvent_shouldPassCorrectOffset() {
        OrderEvent event = buildEvent("order-3", OrderStatus.SHIPPED);
        ConsumerRecord<String, OrderEvent> record = buildRecord(event, 0, 99L);

        listener.onOrderEvent(record, acknowledgment);

        verify(processorService).process(event, 0, 99L);
    }

    @Test
    @DisplayName("onOrderEvent() should acknowledge the offset after successful processing")
    void onOrderEvent_shouldAcknowledgeAfterSuccess() {
        OrderEvent event = buildEvent("order-4", OrderStatus.DELIVERED);
        ConsumerRecord<String, OrderEvent> record = buildRecord(event, 1, 3L);

        listener.onOrderEvent(record, acknowledgment);

        // The Acknowledgment.acknowledge() must be called exactly once
        verify(acknowledgment).acknowledge();
    }

    // ── Failure path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("onOrderEvent() should NOT acknowledge when the processor service throws")
    void onOrderEvent_shouldNotAcknowledgeOnFailure() {
        OrderEvent event = buildEvent("order-fail", OrderStatus.CREATED);
        ConsumerRecord<String, OrderEvent> record = buildRecord(event, 0, 0L);

        // Make the service throw to simulate a processing failure
        doThrow(new RuntimeException("processing error"))
                .when(processorService).process(any(), anyInt(), anyLong());

        // The listener must re-throw so the test catches the exception
        try {
            listener.onOrderEvent(record, acknowledgment);
        } catch (RuntimeException ignored) {
            // Expected – we just want to check acknowledgment was not called
        }

        // The offset must NOT be committed when processing fails
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("onOrderEvent() should re-throw a RuntimeException when the processor service fails")
    void onOrderEvent_shouldRethrowOnProcessingFailure() {
        OrderEvent event = buildEvent("order-rethrow", OrderStatus.CREATED);
        ConsumerRecord<String, OrderEvent> record = buildRecord(event, 0, 7L);

        doThrow(new RuntimeException("broker I/O error"))
                .when(processorService).process(any(), anyInt(), anyLong());

        // The listener must propagate the exception so the Kafka container
        // can apply its retry / dead-letter-topic policy
        assertThatThrownBy(() -> listener.onOrderEvent(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process order event at offset 7");
    }
}
