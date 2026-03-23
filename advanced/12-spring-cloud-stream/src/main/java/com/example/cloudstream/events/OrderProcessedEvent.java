package com.example.cloudstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the Kafka topic {@code orders-processed} after the
 * {@code orderProcessor} Function has enriched the original {@link OrderPlacedEvent}.
 *
 * <p>This is the <strong>second event</strong> in the Spring Cloud Stream pipeline.
 * It is produced by the {@code orderProcessor} (a {@link java.util.function.Function})
 * and consumed by the {@code notificationConsumer} (a {@link java.util.function.Consumer}).
 *
 * <p>Enrichment performed by the processor:
 * <ul>
 *   <li>Adds a processing timestamp.</li>
 *   <li>Adds a computed {@code estimatedDeliveryDays} value.</li>
 *   <li>Adds a human-readable {@code summary} string for the notification.</li>
 * </ul>
 *
 * <p>Kafka message structure:
 * <pre>
 *   Key:   orderId (String)
 *   Value: JSON-serialized OrderProcessedEvent
 * </pre>
 *
 * @param orderId               unique identifier of the original order
 * @param customerId            ID of the customer
 * @param productId             ID of the product
 * @param quantity              number of units
 * @param totalPrice            total cost
 * @param placedAt              timestamp from the original placed event
 * @param processedAt           timestamp when the processor handled this event
 * @param estimatedDeliveryDays processor-computed estimated delivery time
 * @param summary               human-readable description used in the notification
 */
public record OrderProcessedEvent(
        UUID orderId,
        String customerId,
        String productId,
        int quantity,
        BigDecimal totalPrice,
        Instant placedAt,
        Instant processedAt,
        int estimatedDeliveryDays,
        String summary
) {
}
