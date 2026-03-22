package com.example.saga.inventory.events;

import java.util.UUID;

/**
 * Outbound event published by the Inventory Service to topic {@code inventory.reserved}
 * after successfully reserving the requested stock.
 *
 * @param orderId       the order for which stock was reserved
 * @param productId     the product whose stock was decremented
 * @param quantity      number of units reserved
 * @param reservationId unique identifier of the reservation record
 */
public record InventoryReservedEvent(
        UUID orderId,
        String productId,
        int quantity,
        UUID reservationId
) {}
