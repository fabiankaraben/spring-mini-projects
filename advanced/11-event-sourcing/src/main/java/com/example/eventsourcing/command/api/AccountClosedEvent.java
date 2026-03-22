package com.example.eventsourcing.command.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event that records the closure of a bank account.
 *
 * <p>Once this event is in the event log, the aggregate is considered closed and
 * will reject any further commands (deposits, withdrawals, re-closures).
 *
 * <p>Because we use Event Sourcing, even after an account is closed we can still
 * replay the full event history to see every deposit and withdrawal that occurred
 * during the account's lifetime — something impossible with traditional CRUD.
 *
 * @param accountId      the account that was closed
 * @param finalBalance   the account balance at the time of closure
 * @param reason         optional free-text reason for closure
 * @param closedAt       the timestamp of the closure
 */
public record AccountClosedEvent(
        String accountId,
        BigDecimal finalBalance,
        String reason,
        Instant closedAt
) {}
