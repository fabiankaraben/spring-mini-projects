package com.example.eventsourcing.command.aggregate;

/**
 * Represents the lifecycle status of a bank account aggregate.
 *
 * <p>In an Event Sourced system, this enum value is never persisted directly.
 * Instead, it is computed in-memory by replaying events through the
 * {@code @EventSourcingHandler} methods of {@link BankAccountAggregate}.
 *
 * <ul>
 *   <li>{@link #ACTIVE}  — the account is open and fully operational</li>
 *   <li>{@link #CLOSED}  — the account has been closed; no further operations allowed</li>
 * </ul>
 */
public enum AccountStatus {

    /**
     * The account is open and accepts deposits and withdrawals.
     * This is the initial state set by the {@code AccountOpenedEvent}.
     */
    ACTIVE,

    /**
     * The account has been closed.
     * Any command that tries to modify a closed account will be rejected
     * by the aggregate's business-rule validation.
     */
    CLOSED
}
