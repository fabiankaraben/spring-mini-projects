package com.example.kafkaconsumer.domain;

/**
 * Represents the lifecycle status of an order at the time an event is emitted.
 *
 * <p>Each constant maps to a business-meaningful state transition. This consumer
 * application uses this value to decide how to handle the event (e.g. trigger
 * fulfilment logic for {@code CONFIRMED} events, or send notifications for
 * {@code SHIPPED} events).
 *
 * <p>These values must match the enum names used by the Kafka producer so that
 * JSON deserialisation works correctly (Jackson maps JSON string values to enum
 * constants by name).
 */
public enum OrderStatus {

    /**
     * The order has been received and persisted but not yet validated or
     * confirmed. This is the typical initial state when a customer submits
     * an order.
     */
    CREATED,

    /**
     * Payment and inventory checks have passed; the order is confirmed and
     * ready for fulfilment.
     */
    CONFIRMED,

    /**
     * The order has been shipped to the customer.
     */
    SHIPPED,

    /**
     * The order was delivered successfully to the customer.
     */
    DELIVERED,

    /**
     * The order was cancelled before fulfilment.
     */
    CANCELLED
}
