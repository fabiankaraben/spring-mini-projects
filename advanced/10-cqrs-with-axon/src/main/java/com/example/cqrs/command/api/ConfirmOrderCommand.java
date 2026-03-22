package com.example.cqrs.command.api;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Command to confirm a previously placed order.
 *
 * <p>Only an order in {@code PLACED} status can be confirmed. The aggregate enforces
 * this invariant in its {@code @CommandHandler} for this command.
 *
 * @param orderId the identifier of the order to confirm
 */
public record ConfirmOrderCommand(

        @TargetAggregateIdentifier
        String orderId
) {}
