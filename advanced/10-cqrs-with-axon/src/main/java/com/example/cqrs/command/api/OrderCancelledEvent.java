package com.example.cqrs.command.api;

import java.time.Instant;

/**
 * Domain event published when an order has been cancelled.
 *
 * <p>This event transitions the order from {@code PLACED} to {@code CANCELLED} status.
 *
 * @param orderId    unique identifier of the cancelled order
 * @param reason     optional human-readable reason provided by the caller
 * @param occurredOn timestamp when the cancellation happened
 */
public record OrderCancelledEvent(
        String orderId,
        String reason,
        Instant occurredOn
) {}
