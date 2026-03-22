package com.example.eventsourcing.command.api;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/**
 * Command to deposit money into an existing bank account.
 *
 * <p>This command targets an existing {@code BankAccountAggregate} instance.
 * Axon loads the aggregate from the event store (replaying all past events to
 * reconstruct its current state) before routing this command to the
 * {@code @CommandHandler handle(DepositMoneyCommand)} method.
 *
 * @param accountId   the account to deposit into (routes to the correct aggregate)
 * @param amount      the amount to deposit (must be positive)
 * @param description optional free-text description of the deposit (e.g. "Salary")
 */
public record DepositMoneyCommand(

        @TargetAggregateIdentifier
        String accountId,

        BigDecimal amount,

        String description
) {}
