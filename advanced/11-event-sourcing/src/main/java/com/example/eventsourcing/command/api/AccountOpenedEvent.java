package com.example.eventsourcing.command.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain event that records the fact that a bank account was opened.
 *
 * <h2>What is a Domain Event?</h2>
 * A domain event is an immutable record of something that *happened* in the system.
 * It is named in the past tense ("Account Opened") and carries all data that describes
 * the state change.
 *
 * <h2>Event Sourcing principle</h2>
 * This event is the single source of truth for account creation. Instead of storing
 * an account row with an "OPEN" status column, we store this event. When the aggregate
 * is loaded, Axon replays this event first, then all subsequent events, to reconstruct
 * the current state.
 *
 * <h2>Immutability</h2>
 * Events must be immutable — once written to the event store they are never modified.
 * Java records are a natural fit because they are immutable by design.
 *
 * @param accountId      the unique identifier of the account
 * @param ownerName      the full name of the account owner
 * @param initialDeposit the opening balance
 * @param openedAt       the timestamp when the account was opened
 */
public record AccountOpenedEvent(
        String accountId,
        String ownerName,
        BigDecimal initialDeposit,
        Instant openedAt
) {}
