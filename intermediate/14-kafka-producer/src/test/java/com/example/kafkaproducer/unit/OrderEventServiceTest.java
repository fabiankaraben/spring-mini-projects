package com.example.kafkaproducer.unit;

import com.example.kafkaproducer.domain.OrderEvent;
import com.example.kafkaproducer.domain.OrderStatus;
import com.example.kafkaproducer.dto.PublishOrderRequest;
import com.example.kafkaproducer.dto.PublishOrderResponse;
import com.example.kafkaproducer.service.OrderEventService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderEventService}.
 *
 * <p>These tests exercise the service in complete isolation, with no Spring
 * context and no real Kafka broker. The {@link KafkaTemplate} dependency is
 * replaced by a Mockito mock so we can:
 * <ul>
 *   <li>Control what the template returns (happy path, failure).</li>
 *   <li>Verify that the template is called with the correct topic / key.</li>
 *   <li>Run the tests at in-memory speed without any Docker infrastructure.</li>
 * </ul>
 *
 * <h2>Testing approach</h2>
 * <ol>
 *   <li><strong>Happy path</strong> – mock returns a {@link CompletableFuture}
 *       that resolves to a {@link SendResult} with fake {@link RecordMetadata}.
 *       We assert the response DTO fields are populated correctly.</li>
 *   <li><strong>Failure path</strong> – mock returns a failed future. We assert
 *       that the service wraps the cause and re-throws a {@link RuntimeException}.</li>
 *   <li><strong>Interaction verification</strong> – we verify that
 *       {@code kafkaTemplate.send(topic, key, value)} is invoked exactly once
 *       with the expected topic and orderId key.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventService – Unit Tests")
class OrderEventServiceTest {

    /**
     * Mock of the {@link KafkaOperations} interface – simulates the broker
     * without starting Docker. We mock the interface rather than the concrete
     * {@link org.springframework.kafka.core.KafkaTemplate} class because
     * Mockito cannot subclass concrete Spring beans on Java 21+ without
     * additional byte-buddy configuration.
     */
    @Mock
    private KafkaOperations<String, OrderEvent> kafkaTemplate;

    /** The service under test. */
    private OrderEventService orderEventService;

    /** Topic name injected via ReflectionTestUtils (simulates @Value binding). */
    private static final String TEST_TOPIC = "order-events";

    @BeforeEach
    void setUp() {
        // Instantiate the service manually (no Spring context required)
        orderEventService = new OrderEventService(kafkaTemplate);
        // Inject the private @Value field the same way Spring would
        ReflectionTestUtils.setField(orderEventService, "ordersTopic", TEST_TOPIC);
    }

    // ── Helper: build a PublishOrderRequest fixture ───────────────────────────

    private PublishOrderRequest buildRequest(String orderId) {
        return new PublishOrderRequest(
                orderId,
                "cust-42",
                "Widget Pro",
                3,
                new BigDecimal("29.97"),
                OrderStatus.CREATED
        );
    }

    // ── Helper: build a fake SendResult ──────────────────────────────────────

    /**
     * Creates a {@link SendResult} backed by fake {@link RecordMetadata}.
     *
     * <p>RecordMetadata is a final class, so we construct it directly using
     * the constructor that accepts a {@link TopicPartition}, offset, and
     * timestamp. This is the standard way to create test doubles for Kafka
     * metadata without mocking final classes.
     */
    private SendResult<String, OrderEvent> fakeSendResult(int partition, long offset) {
        TopicPartition tp = new TopicPartition(TEST_TOPIC, partition);
        RecordMetadata metadata = new RecordMetadata(tp, offset, 0, System.currentTimeMillis(), 0, 0);
        ProducerRecord<String, OrderEvent> producerRecord =
                new ProducerRecord<>(TEST_TOPIC, "order-1", null);
        return new SendResult<>(producerRecord, metadata);
    }

    // ── Happy path tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("publish() should return a response with the correct orderId")
    void publish_shouldReturnResponseWithCorrectOrderId() {
        // Arrange: mock the template to return a successfully completed future
        SendResult<String, OrderEvent> fakeResult = fakeSendResult(0, 5L);
        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-1"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(fakeResult));

        // Act
        PublishOrderResponse response = orderEventService.publish(buildRequest("order-1"));

        // Assert: orderId is echoed from the request
        assertThat(response.orderId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("publish() should return a response with a non-blank eventId")
    void publish_shouldReturnResponseWithNonBlankEventId() {
        SendResult<String, OrderEvent> fakeResult = fakeSendResult(0, 0L);
        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-2"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(fakeResult));

        PublishOrderResponse response = orderEventService.publish(buildRequest("order-2"));

        // The service delegates eventId generation to OrderEvent.create()
        assertThat(response.eventId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("publish() should return the correct partition and offset from RecordMetadata")
    void publish_shouldReturnCorrectPartitionAndOffset() {
        SendResult<String, OrderEvent> fakeResult = fakeSendResult(2, 42L);
        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-3"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(fakeResult));

        PublishOrderResponse response = orderEventService.publish(buildRequest("order-3"));

        assertThat(response.partition()).isEqualTo(2);
        assertThat(response.offset()).isEqualTo(42L);
    }

    @Test
    @DisplayName("publish() should return the correct topic name")
    void publish_shouldReturnCorrectTopicName() {
        SendResult<String, OrderEvent> fakeResult = fakeSendResult(0, 1L);
        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-4"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(fakeResult));

        PublishOrderResponse response = orderEventService.publish(buildRequest("order-4"));

        assertThat(response.topic()).isEqualTo(TEST_TOPIC);
    }

    @Test
    @DisplayName("publish() should return a non-null timestamp")
    void publish_shouldReturnNonNullTimestamp() {
        SendResult<String, OrderEvent> fakeResult = fakeSendResult(0, 1L);
        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-5"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(fakeResult));

        PublishOrderResponse response = orderEventService.publish(buildRequest("order-5"));

        assertThat(response.timestamp()).isNotNull();
    }

    // ── Interaction verification tests ────────────────────────────────────────

    @Test
    @DisplayName("publish() should call kafkaTemplate.send() with the correct topic and orderId key")
    void publish_shouldInvokeKafkaTemplateWithCorrectTopicAndKey() {
        SendResult<String, OrderEvent> fakeResult = fakeSendResult(0, 0L);
        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-verify"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(fakeResult));

        orderEventService.publish(buildRequest("order-verify"));

        // Verify send was called exactly once with the right topic and key
        verify(kafkaTemplate).send(eq(TEST_TOPIC), eq("order-verify"), any(OrderEvent.class));
    }

    // ── Failure path tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("publish() should throw RuntimeException when KafkaTemplate.send() fails")
    void publish_shouldThrowRuntimeException_whenKafkaSendFails() {
        // Arrange: return a future that has already failed with an exception
        CompletableFuture<SendResult<String, OrderEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unavailable"));

        when(kafkaTemplate.send(eq(TEST_TOPIC), eq("order-fail"), any(OrderEvent.class)))
                .thenReturn(failedFuture);

        // Act & Assert: the service must wrap and re-throw the exception
        assertThatThrownBy(() -> orderEventService.publish(buildRequest("order-fail")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish order event to Kafka");
    }
}
