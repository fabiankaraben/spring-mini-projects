package com.example.cqrs.command.api;

import java.time.Instant;

/**
 * Domain event published when an order has been confirmed.
 *
 * <p>This event transitions the order from {@code PLACED} to {@code CONFIRMED} status.
 * Once confirmed, the order can no longer be cancelled (business rule).
 *
 * @param orderId    unique identifier of the confirmed order
 * @param occurredOn timestamp when the confirmation happened
 */
public record OrderConfirmedEvent(
        String orderId,
        Instant occurredOn
) {}
