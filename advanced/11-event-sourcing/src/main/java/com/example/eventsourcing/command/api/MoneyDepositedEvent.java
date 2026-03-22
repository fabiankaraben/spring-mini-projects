package com.example.eventsourcing.command.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event that records a money deposit on a bank account.
 *
 * <p>This event is stored in the event log (append-only). The account balance is never
 * stored directly — it is always computed by summing all deposits and withdrawals
 * from the event history when the aggregate is reconstructed.
 *
 * @param accountId     the account that received the deposit
 * @param amount        the deposited amount (always positive)
 * @param balanceAfter  the account balance immediately after this deposit
 *                      (stored for convenience in the read model projection)
 * @param description   optional free-text description of the deposit
 * @param occurredAt    the timestamp of the deposit
 */
public record MoneyDepositedEvent(
        String accountId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant occurredAt
) {}
