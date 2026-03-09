package com.example.kafkaproducer.domain;

/**
 * Represents the lifecycle status of an order at the time an event is emitted.
 *
 * <p>Each constant maps to a business-meaningful state transition. Downstream
 * Kafka consumers use this value to decide how to process the event (e.g. only
 * fulfil orders in the {@code CONFIRMED} state).
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
