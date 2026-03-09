package com.example.kafkaproducer.service;

import com.example.kafkaproducer.domain.OrderEvent;
import com.example.kafkaproducer.dto.PublishOrderRequest;
import com.example.kafkaproducer.dto.PublishOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for converting an incoming order request into an
 * {@link OrderEvent} and publishing it to the configured Kafka topic.
 *
 * <h2>How KafkaTemplate works</h2>
 * <p>{@link KafkaTemplate} is the core Spring Kafka class for sending messages.
 * Internally it:
 * <ol>
 *   <li>Serialises the key (order ID string) and value ({@link OrderEvent}
 *       serialised to JSON by the Jackson serialiser configured in
 *       {@code application.yml}) into byte arrays.</li>
 *   <li>Selects a partition using the configured partitioner (round-robin by
 *       default when no explicit key is used; sticky when a key is provided).</li>
 *   <li>Buffers the record in the producer's in-memory batch and flushes it to
 *       the broker asynchronously.</li>
 *   <li>Returns a {@link CompletableFuture} that completes when the broker
 *       acknowledges the write.</li>
 * </ol>
 *
 * <h2>Key-based partitioning</h2>
 * <p>We use {@code orderId} as the Kafka message key. This ensures that all
 * events for the same order are routed to the same partition, which guarantees
 * ordering for a given order. Consumers reading from a single partition receive
 * the events in the order they were produced.
 */
@Service
public class OrderEventService {

    private static final Logger log = LoggerFactory.getLogger(OrderEventService.class);

    /**
     * Spring Kafka's producer abstraction, typed as the {@link KafkaOperations}
     * interface rather than the concrete {@link KafkaTemplate} class.
     * Using the interface makes this field mockable in unit tests without
     * requiring bytecode manipulation of the final class.
     * Parameterised with {@code <String, OrderEvent>}:
     * - {@code String}     → the message key (orderId)
     * - {@code OrderEvent} → the message value (serialised to JSON)
     */
    private final KafkaOperations<String, OrderEvent> kafkaTemplate;

    /** The Kafka topic name where order events are published. */
    @Value("${app.kafka.topic.orders}")
    private String ordersTopic;

    /**
     * Constructor injection – preferred over field injection for testability.
     * Spring Boot auto-configures the {@link KafkaTemplate} based on the
     * properties defined in {@code application.yml}.
     *
     * @param kafkaTemplate the Kafka producer template provided by Spring
     */
    public OrderEventService(KafkaOperations<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes an order event to Kafka and returns metadata about the send.
     *
     * <p>The method:
     * <ol>
     *   <li>Builds an {@link OrderEvent} from the request data using the
     *       factory method (which auto-generates the event ID and timestamp).</li>
     *   <li>Calls {@link KafkaTemplate#send(String, String, Object)} with the
     *       topic, key, and value. The key is the {@code orderId} to guarantee
     *       ordering per order in the same partition.</li>
     *   <li>Blocks on the resulting {@link CompletableFuture} to get the
     *       {@link SendResult}, which contains the {@code RecordMetadata}
     *       (topic, partition, offset) assigned by the broker.</li>
     * </ol>
     *
     * <p><strong>Note on blocking:</strong> {@code .get()} is used here to
     * wait for the broker acknowledgement before returning a response to the
     * caller. This simplifies error handling in the REST layer at the cost of
     * latency. High-throughput producers should use the async approach (chain
     * callbacks on the {@link CompletableFuture} without blocking).
     *
     * @param request the validated order request from the REST controller
     * @return a {@link PublishOrderResponse} containing the event ID and
     *         the Kafka partition / offset metadata
     * @throws RuntimeException if the Kafka send fails or the broker is
     *                          unreachable
     */
    public PublishOrderResponse publish(PublishOrderRequest request) {
        // Build the domain event from the request data
        OrderEvent event = OrderEvent.create(
                request.orderId(),
                request.customerId(),
                request.product(),
                request.quantity(),
                request.totalAmount(),
                request.status()
        );

        log.info("Publishing order event [eventId={}, orderId={}, status={}] to topic '{}'",
                event.eventId(), event.orderId(), event.status(), ordersTopic);

        // Send the event to Kafka using orderId as the message key so that
        // all events for the same order land on the same partition.
        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(ordersTopic, event.orderId(), event);

        try {
            // Wait for the broker to acknowledge the message.
            // SendResult gives us the RecordMetadata (topic, partition, offset).
            SendResult<String, OrderEvent> result = future.get();

            int partition = result.getRecordMetadata().partition();
            long offset    = result.getRecordMetadata().offset();

            log.info("Order event published successfully [eventId={}, topic={}, partition={}, offset={}]",
                    event.eventId(), ordersTopic, partition, offset);

            // Build and return the response DTO
            return new PublishOrderResponse(
                    event.eventId(),
                    event.orderId(),
                    event.status(),
                    ordersTopic,
                    partition,
                    offset,
                    event.occurredAt()
            );

        } catch (InterruptedException e) {
            // Restore the interrupted flag before propagating
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for Kafka acknowledgement", e);
        } catch (Exception e) {
            log.error("Failed to publish order event [orderId={}]: {}", event.orderId(), e.getMessage());
            throw new RuntimeException("Failed to publish order event to Kafka: " + e.getMessage(), e);
        }
    }
}
