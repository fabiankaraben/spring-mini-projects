package com.example.eventsourcing.query.handler;

import com.example.eventsourcing.command.api.*;
import com.example.eventsourcing.query.api.FindAccountByIdQuery;
import com.example.eventsourcing.query.api.FindAllAccountsQuery;
import com.example.eventsourcing.query.model.AccountSummary;
import com.example.eventsourcing.query.model.AccountSummaryRepository;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The {@code AccountProjection} maintains the read-model ({@link AccountSummary}) by
 * listening to domain events published by the {@code BankAccountAggregate}.
 *
 * <h2>Role of the projection in Event Sourcing</h2>
 * The event store is the authoritative source of truth, but querying it directly
 * (replaying all events for every read request) would be expensive. The projection
 * solves this by:
 * <ol>
 *   <li>Listening to every domain event as it is published</li>
 *   <li>Updating the {@code account_summaries} JPA table accordingly</li>
 *   <li>Providing fast {@code @QueryHandler} methods for REST read endpoints</li>
 * </ol>
 *
 * <h2>Event handlers vs. Query handlers</h2>
 * <ul>
 *   <li>{@code @EventHandler} methods — called when a domain event is published;
 *       they update the read model (write to {@code account_summaries})</li>
 *   <li>{@code @QueryHandler} methods — called by Axon's QueryBus when a query
 *       object is dispatched; they read from the read model</li>
 * </ul>
 *
 * <h2>Synchronous update (Subscribing processor)</h2>
 * Because we configured {@code usingSubscribingEventProcessors()} in {@code AxonConfig},
 * these {@code @EventHandler} methods run on the same thread and within the same transaction
 * as the command handler. This means the read model is fully up-to-date by the time
 * {@code CommandGateway.sendAndWait()} returns — no eventual consistency lag.
 */
@Component
public class AccountProjection {

    /** JPA repository for reading/writing the account_summaries table. */
    private final AccountSummaryRepository repository;

    public AccountProjection(AccountSummaryRepository repository) {
        this.repository = repository;
    }

    // =========================================================================
    //  Event Handlers — update the read model when events arrive
    // =========================================================================

    /**
     * Handles {@link AccountOpenedEvent} — creates a new row in {@code account_summaries}.
     *
     * <p>This is the first event in every account's lifecycle. It creates the initial
     * projection entry with status "ACTIVE" and the opening balance.
     *
     * @param event the account-opened event from the event store
     */
    @EventHandler
    public void on(AccountOpenedEvent event) {
        AccountSummary summary = new AccountSummary(
                event.accountId(),
                event.ownerName(),
                event.initialDeposit(),
                event.openedAt()
        );
        repository.save(summary);
    }

    /**
     * Handles {@link MoneyDepositedEvent} — updates the balance in the read model.
     *
     * <p>Instead of recalculating the balance from scratch, we use the
     * {@code balanceAfter} field that was captured in the event at the time of
     * emission, ensuring the projection stays consistent with the aggregate state.
     *
     * @param event the money-deposited event
     */
    @EventHandler
    public void on(MoneyDepositedEvent event) {
        repository.findById(event.accountId()).ifPresent(summary -> {
            summary.setBalance(event.balanceAfter());
            summary.setUpdatedAt(event.occurredAt());
            repository.save(summary);
        });
    }

    /**
     * Handles {@link MoneyWithdrawnEvent} — updates the balance in the read model.
     *
     * @param event the money-withdrawn event
     */
    @EventHandler
    public void on(MoneyWithdrawnEvent event) {
        repository.findById(event.accountId()).ifPresent(summary -> {
            summary.setBalance(event.balanceAfter());
            summary.setUpdatedAt(event.occurredAt());
            repository.save(summary);
        });
    }

    /**
     * Handles {@link AccountClosedEvent} — marks the account as CLOSED in the read model.
     *
     * <p>Note: even after an account is closed, its full event history remains in the
     * event store and can be replayed at any time — a key advantage of Event Sourcing.
     *
     * @param event the account-closed event
     */
    @EventHandler
    public void on(AccountClosedEvent event) {
        repository.findById(event.accountId()).ifPresent(summary -> {
            summary.setStatus("CLOSED");
            summary.setUpdatedAt(event.closedAt());
            repository.save(summary);
        });
    }

    // =========================================================================
    //  Query Handlers — serve reads from the read model
    // =========================================================================

    /**
     * Handles {@link FindAccountByIdQuery} — returns a single account summary.
     *
     * <p>Axon routes this query here via the {@code QueryBus} when the REST controller
     * calls {@code queryGateway.query(new FindAccountByIdQuery(id), ...)}.
     *
     * @param query the find-by-id query
     * @return the matching {@link AccountSummary}, or {@code Optional.empty()} if not found
     */
    @QueryHandler
    public Optional<AccountSummary> handle(FindAccountByIdQuery query) {
        return repository.findById(query.accountId());
    }

    /**
     * Handles {@link FindAllAccountsQuery} — returns all accounts, optionally filtered by status.
     *
     * @param query the find-all query (may contain a status filter)
     * @return list of matching account summaries
     */
    @QueryHandler
    public List<AccountSummary> handle(FindAllAccountsQuery query) {
        if (query.statusFilter() != null && !query.statusFilter().isBlank()) {
            // Filter by status (e.g. GET /api/accounts?status=ACTIVE)
            return repository.findByStatus(query.statusFilter().toUpperCase());
        }
        // Return all accounts
        return repository.findAll();
    }
}
