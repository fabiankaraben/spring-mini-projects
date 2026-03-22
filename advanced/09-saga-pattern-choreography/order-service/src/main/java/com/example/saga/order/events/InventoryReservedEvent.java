package com.example.saga.order.events;

import java.util.UUID;

/**
 * Domain event published by the Inventory Service when stock has been successfully
 * reserved for an order.
 *
 * <p>This is the <em>final success event</em> in the saga. On receiving it, the
 * Order Service marks the order as {@code COMPLETED}.
 *
 * <p>Published on Kafka topic: {@code inventory.reserved}.
 *
 * @param orderId      the order for which inventory was reserved
 * @param productId    the product whose stock was decremented
 * @param quantity     the number of units reserved
 * @param reservationId unique identifier assigned by the Inventory Service for this reservation
 */
public record InventoryReservedEvent(
        UUID orderId,
        String productId,
        int quantity,
        UUID reservationId
) {}
