package com.example.kafkaproducer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain object representing an order event that is published to Kafka.
 *
 * <p>This record captures a snapshot of an order at the moment it is placed.
 * It is serialised to JSON by Jackson and sent as the Kafka message value.
 *
 * <p>Using a Java {@code record} ensures immutability: once an
 * {@code OrderEvent} is created its fields cannot change, which is important
 * for event-sourcing patterns where published events should never be mutated.
 *
 * @param eventId      unique identifier for this specific event (UUID v4)
 * @param orderId      identifier of the order that triggered the event
 * @param customerId   identifier of the customer who placed the order
 * @param product      name or SKU of the product being ordered
 * @param quantity     number of units ordered (must be positive)
 * @param totalAmount  total monetary value of the order
 * @param status       current status of the order (e.g. CREATED, CANCELLED)
 * @param occurredAt   UTC timestamp when the event was generated
 */
public record OrderEvent(
        String eventId,
        String orderId,
        String customerId,
        String product,
        int quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant occurredAt
) {

    /**
     * Factory method that creates an {@code OrderEvent} with a freshly
     * generated {@code eventId} and the current UTC timestamp.
     *
     * <p>Callers only need to supply the business fields; infrastructure
     * fields ({@code eventId} and {@code occurredAt}) are set automatically.
     *
     * @param orderId     identifier of the order
     * @param customerId  identifier of the customer
     * @param product     product name or SKU
     * @param quantity    number of units
     * @param totalAmount total monetary value
     * @param status      order status at the time of the event
     * @return a fully populated {@code OrderEvent} ready to be published
     */
    public static OrderEvent create(
            String orderId,
            String customerId,
            String product,
            int quantity,
            BigDecimal totalAmount,
            OrderStatus status) {

        return new OrderEvent(
                UUID.randomUUID().toString(), // auto-generated event ID
                orderId,
                customerId,
                product,
                quantity,
                totalAmount,
                status,
                Instant.now()                 // current UTC timestamp
        );
    }
}
