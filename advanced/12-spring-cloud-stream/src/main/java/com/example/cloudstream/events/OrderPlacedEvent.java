package com.example.cloudstream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the Kafka topic {@code orders} when a new order is placed.
 *
 * <p>This is the <strong>first event</strong> in the Spring Cloud Stream pipeline.
 * It is produced by the {@code orderSupplier} (a {@link java.util.function.Supplier})
 * and consumed by the {@code orderProcessor} (a {@link java.util.function.Function}).
 *
 * <p>Why a record?
 * Java records are ideal for events/DTOs because:
 * <ul>
 *   <li>They are immutable — events should never be mutated after publishing.</li>
 *   <li>They generate {@code equals}, {@code hashCode}, and {@code toString} automatically.</li>
 *   <li>Jackson can deserialize them as long as the all-args constructor is used.</li>
 * </ul>
 *
 * <p>Kafka message structure:
 * <pre>
 *   Key:   orderId (String)
 *   Value: JSON-serialized OrderPlacedEvent
 * </pre>
 *
 * @param orderId     unique identifier of the order (used as Kafka message key)
 * @param customerId  ID of the customer who placed the order
 * @param productId   ID of the product ordered
 * @param quantity    number of units ordered
 * @param totalPrice  total cost of the order
 * @param placedAt    timestamp when the order was placed
 */
public record OrderPlacedEvent(
        UUID orderId,
        String customerId,
        String productId,
        int quantity,
        BigDecimal totalPrice,
        Instant placedAt
) {

    /**
     * Convenience factory method to create an event from a domain {@link com.example.cloudstream.domain.Order}.
     *
     * @param order the source domain object
     * @return a new {@link OrderPlacedEvent} populated from the order
     */
    public static OrderPlacedEvent from(com.example.cloudstream.domain.Order order) {
        return new OrderPlacedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getCreatedAt()
        );
    }
}
