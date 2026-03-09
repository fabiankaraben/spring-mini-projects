package com.example.rabbitmqconsumer.service;

import com.example.rabbitmqconsumer.domain.OrderMessage;
import com.example.rabbitmqconsumer.service.MessageConsumerService.ProcessedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MessageConsumerService}.
 *
 * <p>These tests exercise the service's business logic in pure isolation:
 * <ul>
 *   <li>No Spring context is loaded — no AMQP infrastructure, no HTTP server,
 *       no Docker containers. Tests run in milliseconds.</li>
 *   <li>The {@code @RabbitListener} wiring is <em>not</em> tested here; it is
 *       covered by the integration tests in
 *       {@link com.example.rabbitmqconsumer.MessageConsumerIntegrationTest}.</li>
 *   <li>We test the processing logic ({@code processOrder}) and the listener
 *       entry-point ({@code consumeOrder}) independently to keep each test
 *       focused on a single responsibility.</li>
 *   <li>Each test follows the Given / When / Then pattern for clarity.</li>
 * </ul>
 */
@DisplayName("MessageConsumerService unit tests")
class MessageConsumerServiceTest {

    /**
     * The class under test. Instantiated directly (no Mockito, no Spring)
     * because the service has no external dependencies beyond its own state.
     */
    private MessageConsumerService service;

    /**
     * Reset the service state before every test so tests are independent.
     */
    @BeforeEach
    void setUp() {
        // A fresh service instance guarantees zero processed messages at test start
        service = new MessageConsumerService();
    }

    // ── processOrder: message count ───────────────────────────────────────────────

    @Test
    @DisplayName("processOrder increments the message count by 1")
    void processOrder_incrementsMessageCount() {
        // Given: a valid order message
        OrderMessage message = buildMessage("msg-1", "ORD-001", "Laptop", 2);

        // When: process the message
        service.processOrder(message);

        // Then: the count must be 1
        assertThat(service.getMessageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("processOrder increments the message count for each call")
    void processOrder_incrementsCountForEachCall() {
        // Given: three different messages
        service.processOrder(buildMessage("m1", "ORD-A", "Product A", 1));
        service.processOrder(buildMessage("m2", "ORD-B", "Product B", 2));
        service.processOrder(buildMessage("m3", "ORD-C", "Product C", 3));

        // Then: count must be 3
        assertThat(service.getMessageCount()).isEqualTo(3L);
    }

    // ── processOrder: processed message log ──────────────────────────────────────

    @Test
    @DisplayName("processOrder stores the message in the processed log")
    void processOrder_storesMessageInLog() {
        // Given: a valid order message
        OrderMessage message = buildMessage("uuid-42", "ORD-042", "Wireless Mouse", 5);

        // When
        service.processOrder(message);

        // Then: the log must contain exactly one entry
        List<ProcessedMessage> log = service.getProcessedMessages();
        assertThat(log).hasSize(1);

        // And: the stored record must reflect the original message fields
        ProcessedMessage record = log.get(0);
        assertThat(record.messageId()).isEqualTo("uuid-42");
        assertThat(record.orderId()).isEqualTo("ORD-042");
        assertThat(record.product()).isEqualTo("Wireless Mouse");
        assertThat(record.quantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("processOrder sets a non-null processedAt timestamp on the log entry")
    void processOrder_setsProcessedAtTimestamp() {
        // Given: record the time window around the call
        Instant before = Instant.now();
        service.processOrder(buildMessage("id", "ORD-TS", "Keyboard", 1));
        Instant after = Instant.now();

        // Then: processedAt must fall within [before, after]
        ProcessedMessage record = service.getProcessedMessages().get(0);
        assertThat(record.processedAt())
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("processOrder preserves insertion order in the log")
    void processOrder_preservesInsertionOrder() {
        // When: process three messages in a specific order
        service.processOrder(buildMessage("id1", "ORD-1", "Alpha", 1));
        service.processOrder(buildMessage("id2", "ORD-2", "Beta", 2));
        service.processOrder(buildMessage("id3", "ORD-3", "Gamma", 3));

        // Then: the log must preserve the same order
        List<ProcessedMessage> log = service.getProcessedMessages();
        assertThat(log).extracting(ProcessedMessage::orderId)
                .containsExactly("ORD-1", "ORD-2", "ORD-3");
    }

    // ── consumeOrder: happy path ──────────────────────────────────────────────────

    @Test
    @DisplayName("consumeOrder processes a valid message and increments the count")
    void consumeOrder_validMessage_incrementsCount() {
        // Given: a valid, fully populated message
        OrderMessage message = buildMessage("msg-ok", "ORD-OK", "Headphones", 3);

        // When: invoke the listener entry-point directly
        service.consumeOrder(message);

        // Then: the message count must have incremented
        assertThat(service.getMessageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("consumeOrder adds a valid message to the processed log")
    void consumeOrder_validMessage_appearsInLog() {
        // Given
        OrderMessage message = buildMessage("msg-log", "ORD-LOG", "Monitor", 1);

        // When
        service.consumeOrder(message);

        // Then: one entry in the log with matching orderId
        assertThat(service.getProcessedMessages())
                .hasSize(1)
                .first()
                .extracting(ProcessedMessage::orderId)
                .isEqualTo("ORD-LOG");
    }

    // ── consumeOrder: validation / rejection ──────────────────────────────────────

    @Test
    @DisplayName("consumeOrder throws IllegalArgumentException when orderId is null")
    void consumeOrder_nullOrderId_throwsException() {
        // Given: a message with a null orderId
        OrderMessage badMessage = buildMessage("bad-id", null, "Product", 1);

        // When / Then: the service must reject the message (Spring AMQP will NACK it)
        assertThatThrownBy(() -> service.consumeOrder(badMessage))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consumeOrder throws IllegalArgumentException when orderId is blank")
    void consumeOrder_blankOrderId_throwsException() {
        // Given: a message with a blank orderId (whitespace only)
        OrderMessage badMessage = buildMessage("bad-id", "   ", "Product", 1);

        // When / Then
        assertThatThrownBy(() -> service.consumeOrder(badMessage))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consumeOrder does NOT increment count when it throws an exception")
    void consumeOrder_exception_doesNotIncrementCount() {
        // Given: a message with invalid orderId (will cause an exception before processOrder)
        OrderMessage badMessage = buildMessage("bad-id", null, "Product", 1);

        // When: attempt to consume (will throw)
        try {
            service.consumeOrder(badMessage);
        } catch (IllegalArgumentException ignored) {
            // Expected — the exception is what we want to trigger here
        }

        // Then: the count must remain 0 because processing was aborted before the increment
        assertThat(service.getMessageCount()).isZero();
    }

    // ── reset ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset clears the message count to zero")
    void reset_clearsMessageCount() {
        // Given: some messages have been processed
        service.processOrder(buildMessage("r1", "ORD-R1", "X", 1));
        service.processOrder(buildMessage("r2", "ORD-R2", "Y", 2));
        assertThat(service.getMessageCount()).isEqualTo(2L);

        // When
        service.reset();

        // Then
        assertThat(service.getMessageCount()).isZero();
    }

    @Test
    @DisplayName("reset clears the processed message log")
    void reset_clearsProcessedLog() {
        // Given
        service.processOrder(buildMessage("r1", "ORD-R1", "X", 1));
        assertThat(service.getProcessedMessages()).hasSize(1);

        // When
        service.reset();

        // Then
        assertThat(service.getProcessedMessages()).isEmpty();
    }

    // ── getProcessedMessages: immutability ────────────────────────────────────────

    @Test
    @DisplayName("getProcessedMessages returns an unmodifiable list")
    void getProcessedMessages_returnsUnmodifiableList() {
        // Given: one processed message
        service.processOrder(buildMessage("i1", "ORD-I1", "Z", 1));

        // When: obtain the list and try to mutate it
        List<ProcessedMessage> list = service.getProcessedMessages();

        // Then: mutation must throw — the list is unmodifiable
        assertThatThrownBy(() -> list.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getMessageCount returns 0 before any messages are processed")
    void getMessageCount_returnsZeroInitially() {
        // No setup needed — fresh service from @BeforeEach
        assertThat(service.getMessageCount()).isZero();
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    /**
     * Build an {@link OrderMessage} with the given fields and a fixed {@code createdAt}
     * timestamp. Keeps test code concise.
     */
    private OrderMessage buildMessage(String messageId, String orderId,
                                      String product, int quantity) {
        return new OrderMessage(messageId, orderId, product, quantity,
                Instant.parse("2024-01-01T00:00:00Z"));
    }
}
