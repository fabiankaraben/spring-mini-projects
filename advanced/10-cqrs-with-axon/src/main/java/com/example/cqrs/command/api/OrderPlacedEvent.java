package com.example.cqrs.command.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event published when a new order has been successfully placed.
 *
 * <p>In Event Sourcing, <em>events</em> are the source of truth. The {@code OrderAggregate}
 * state is never persisted directly — instead, these events are stored in the Axon event store,
 * and the aggregate is <em>replayed</em> by processing them in sequence when needed.
 *
 * <p>Events are named in the past tense (OrderPlaced, OrderConfirmed, …) to reflect that
 * something has <strong>already happened</strong> — they are immutable facts.
 *
 * <p>This event is consumed by:
 * <ul>
 *   <li>{@code OrderAggregate} — via {@code @EventSourcingHandler} to update in-memory state</li>
 *   <li>{@code OrderProjection} — via {@code @EventHandler} to create a read-model entry</li>
 * </ul>
 *
 * @param orderId    unique identifier of the order
 * @param productId  identifier of the ordered product
 * @param quantity   number of units ordered
 * @param unitPrice  price per unit at the time of order placement
 * @param occurredOn timestamp when the event was created
 */
public record OrderPlacedEvent(
        String orderId,
        String productId,
        int quantity,
        BigDecimal unitPrice,
        Instant occurredOn
) {}
