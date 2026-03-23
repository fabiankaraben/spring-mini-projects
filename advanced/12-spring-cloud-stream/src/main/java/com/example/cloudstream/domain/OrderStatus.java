package com.example.cloudstream.domain;

/**
 * Lifecycle states of an {@link Order} as it moves through the event-driven pipeline.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING
 *     │  (OrderPlacedEvent published to Kafka "orders" topic)
 *     ▼
 *   PROCESSING
 *     │  (OrderProcessedEvent published to Kafka "orders-processed" topic)
 *     ▼
 *   NOTIFIED    ← notification consumer received and logged the event
 *     │
 *     ▼  (only if validation fails during processing)
 *   REJECTED
 * </pre>
 */
public enum OrderStatus {

    /**
     * Order has been received by the REST API and saved in the in-memory store,
     * but not yet processed by the Spring Cloud Stream pipeline.
     */
    PENDING,

    /**
     * The {@code orderProcessor} Function has consumed the event from the
     * "orders" topic and published an enriched event to "orders-processed".
     */
    PROCESSING,

    /**
     * The {@code notificationConsumer} has consumed the event from the
     * "orders-processed" topic and logged a simulated notification.
     */
    NOTIFIED,

    /**
     * The order was rejected during processing (e.g. invalid product or
     * negative total price). A rejection event is published to the
     * "orders-rejected" topic.
     */
    REJECTED
}
