package com.example.cqrs.command.api;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to cancel an existing order.
 *
 * <p>An order can only be cancelled if it is in {@code PLACED} status.
 * Once confirmed, cancellation is no longer allowed (business rule enforced by the aggregate).
 *
 * @param orderId the identifier of the order to cancel
 * @param reason  optional human-readable reason for the cancellation
 */
public record CancelOrderCommand(

        @TargetAggregateIdentifier
        String orderId,

        String reason
) {}
