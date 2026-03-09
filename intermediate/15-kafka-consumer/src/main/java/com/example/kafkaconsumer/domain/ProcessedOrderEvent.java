package com.example.kafkaconsumer.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents an {@link OrderEvent} that has been successfully consumed from
 * Kafka and processed by the application.
 *
 * <p>This record extends the raw event data with consumer-side metadata:
 * the Kafka partition and offset where the message was read from, and the
 * timestamp at which the consumer processed it. This information is valuable
 * for debugging, auditing, and replayability.
 *
 * <p>Instances of this class are stored in an in-memory list by
 * {@link com.example.kafkaconsumer.service.OrderEventProcessorService} and
 * exposed through the REST API so callers can inspect what the consumer has
 * received.
 *
 * <h2>Design note</h2>
 * <p>In a production system you would persist processed events to a database
 * (e.g. PostgreSQL or MongoDB) rather than keeping them in memory. This
 * in-memory approach is intentional for the mini-project to keep the demo
 * focused on Kafka concepts without introducing persistence complexity.
 *
 * @param eventId      unique identifier of the original event (UUID v4)
 * @param orderId      business identifier of the order
 * @param customerId   identifier of the customer who placed the order
 * @param product      product name or SKU being ordered
 * @param quantity     number of units ordered
 * @param totalAmount  total monetary value of the order
 * @param status       order status at the time the event was emitted
 * @param occurredAt   UTC timestamp when the producer emitted the event
 * @param partition    Kafka partition the message was consumed from
 * @param offset       offset of the message within the partition
 * @param processedAt  UTC timestamp when this consumer finished processing the event
 */
public record ProcessedOrderEvent(
        String eventId,
        String orderId,
        String customerId,
        String product,
        int quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant occurredAt,
        int partition,
        long offset,
        Instant processedAt
) {

    /**
     * Factory method that creates a {@code ProcessedOrderEvent} from a raw
     * {@link OrderEvent} and the Kafka partition/offset metadata captured
     * by the listener.
     *
     * <p>The {@code processedAt} timestamp is set to the current UTC instant
     * to record when the consumer finished processing the event.
     *
     * @param event     the raw event deserialised from the Kafka message value
     * @param partition the Kafka partition the message was consumed from
     * @param offset    the offset of the message within the partition
     * @return a fully populated {@code ProcessedOrderEvent}
     */
    public static ProcessedOrderEvent from(OrderEvent event, int partition, long offset) {
        return new ProcessedOrderEvent(
                event.eventId(),
                event.orderId(),
                event.customerId(),
                event.product(),
                event.quantity(),
                event.totalAmount(),
                event.status(),
                event.occurredAt(),
                partition,
                offset,
                Instant.now()  // timestamp when this consumer processed the event
        );
    }
}
