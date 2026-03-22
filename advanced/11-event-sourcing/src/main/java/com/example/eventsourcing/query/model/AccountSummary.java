package com.example.eventsourcing.query.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The read-model projection for a bank account.
 *
 * <h2>Read model (query side)</h2>
 * In an Event Sourced system there are two separate data stores:
 * <ul>
 *   <li><strong>Write side (event store)</strong> — the {@code domain_event_entry} table managed
 *       by Axon, containing the immutable stream of events. This is the <em>single source of truth</em>.</li>
 *   <li><strong>Read side (projection)</strong> — this {@code AccountSummary} JPA entity, stored
 *       in the {@code account_summaries} table. It is a denormalised, query-optimised view
 *       built by listening to the event stream via {@code AccountProjection}.</li>
 * </ul>
 *
 * <h2>Why a separate read model?</h2>
 * Replaying the full event history every time someone calls {@code GET /accounts/{id}}
 * would be inefficient. The projection maintains a precomputed, up-to-date snapshot in
 * a regular relational table so queries are fast O(1) lookups.
 *
 * <h2>Eventual consistency</h2>
 * Because we use SUBSCRIBING event processors (see {@code AxonConfig}), the projection
 * is updated synchronously within the same transaction, so there is no lag between
 * command execution and the read model being updated in this single-process setup.
 */
@Entity
@Table(name = "account_summaries")
public class AccountSummary {

    /** The account ID — mirrors the aggregate identifier in the event store. */
    @Id
    @Column(name = "account_id", nullable = false)
    private String accountId;

    /** The account owner's full name. */
    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    /**
     * The current balance — updated whenever a MoneyDepositedEvent or MoneyWithdrawnEvent
     * is processed by the projection. This is a denormalised field that would otherwise
     * require replaying all events.
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /** The account status: "ACTIVE" or "CLOSED". */
    @Column(name = "status", nullable = false)
    private String status;

    /** Timestamp when the account was opened (from AccountOpenedEvent). */
    @Column(name = "opened_at")
    private Instant openedAt;

    /** Timestamp of the last account update (deposit, withdrawal, or closure). */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * No-arg constructor required by JPA.
     */
    protected AccountSummary() {}

    /**
     * Constructs a new {@code AccountSummary} from an account-opened event.
     *
     * @param accountId      the account identifier
     * @param ownerName      the owner's full name
     * @param initialBalance the opening balance
     * @param openedAt       the timestamp when the account was opened
     */
    public AccountSummary(String accountId, String ownerName, BigDecimal initialBalance, Instant openedAt) {
        this.accountId = accountId;
        this.ownerName = ownerName;
        this.balance = initialBalance;
        this.status = "ACTIVE";
        this.openedAt = openedAt;
        this.updatedAt = openedAt;
    }

    // =========================================================================
    //  Getters and setters
    // =========================================================================

    public String getAccountId() {
        return accountId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
