package com.example.eventsourcing.command.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event that records a money withdrawal from a bank account.
 *
 * <p>This event is only emitted when the aggregate has validated that the account
 * has sufficient funds. The balance is never stored as a mutable row — it is always
 * the result of replaying {@link AccountOpenedEvent}, all {@link MoneyDepositedEvent}s,
 * and all {@link MoneyWithdrawnEvent}s in chronological order.
 *
 * @param accountId     the account from which money was withdrawn
 * @param amount        the withdrawn amount (always positive)
 * @param balanceAfter  the account balance immediately after this withdrawal
 * @param description   optional free-text description (e.g. "ATM withdrawal")
 * @param occurredAt    the timestamp of the withdrawal
 */
public record MoneyWithdrawnEvent(
        String accountId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant occurredAt
) {}
