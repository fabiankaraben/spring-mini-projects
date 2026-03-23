package com.example.cloudstream.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the Kafka topic {@code orders-rejected} when the
 * {@code orderProcessor} Function determines that an order is invalid.
 *
 * <p>Validation rules applied by the processor:
 * <ul>
 *   <li>Total price must be positive (> 0).</li>
 *   <li>Quantity must be positive (> 0).</li>
 *   <li>Product ID must not be blank.</li>
 * </ul>
 *
 * <p>The rejection event is published instead of an {@link OrderProcessedEvent}
 * when any of the above rules fail.
 *
 * <p>Kafka message structure:
 * <pre>
 *   Key:   orderId (String)
 *   Value: JSON-serialized OrderRejectedEvent
 * </pre>
 *
 * @param orderId    unique identifier of the rejected order
 * @param customerId ID of the customer
 * @param productId  ID of the product
 * @param reason     human-readable description of why the order was rejected
 * @param rejectedAt timestamp when the rejection was determined
 */
public record OrderRejectedEvent(
        UUID orderId,
        String customerId,
        String productId,
        String reason,
        Instant rejectedAt
) {
}
