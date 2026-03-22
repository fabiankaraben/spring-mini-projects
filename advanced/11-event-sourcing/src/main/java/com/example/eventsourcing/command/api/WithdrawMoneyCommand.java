package com.example.eventsourcing.command.api;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/**
 * Command to withdraw money from an existing bank account.
 *
 * <p>The aggregate will validate that the account has sufficient balance before
 * emitting a {@code MoneyWithdrawnEvent}. If the balance would go negative,
 * the command handler throws an {@code InsufficientFundsException}.
 *
 * @param accountId   the account to withdraw from
 * @param amount      the amount to withdraw (must be positive)
 * @param description optional free-text description (e.g. "ATM withdrawal")
 */
public record WithdrawMoneyCommand(

        @TargetAggregateIdentifier
        String accountId,

        BigDecimal amount,

        String description
) {}
