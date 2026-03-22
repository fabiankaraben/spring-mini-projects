package com.example.eventsourcing.command.api;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/**
 * Command to open a new bank account.
 *
 * <h2>What is a Command?</h2>
 * A command expresses an *intent* to change the system state. It is named in the
 * imperative ("Open Account") and carries all data required to perform the operation.
 *
 * <p>Commands are dispatched to the {@code CommandBus} and routed by Axon to the
 * correct {@code @CommandHandler} method. If the command targets an existing aggregate,
 * Axon uses {@code @TargetAggregateIdentifier} to load that aggregate from the event store.
 *
 * <h2>Records as commands</h2>
 * Java records are ideal for commands: they are immutable value objects with no
 * behaviour — just data carriers. The {@code @TargetAggregateIdentifier} annotation
 * tells Axon which field holds the aggregate ID.
 *
 * @param accountId     the unique identifier for the new account (UUID string)
 * @param ownerName     the full name of the account owner
 * @param initialDeposit the opening balance (must be >= 0)
 */
public record OpenAccountCommand(

        /**
         * The target aggregate identifier.
         * Axon uses this field to route the command to the correct BankAccountAggregate
         * instance. For new aggregates (creation commands), this will be the new aggregate's ID.
         */
        @TargetAggregateIdentifier
        String accountId,

        String ownerName,

        BigDecimal initialDeposit
) {}
