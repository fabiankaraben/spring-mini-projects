package com.example.eventsourcing.command.aggregate;

import com.example.eventsourcing.command.api.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The {@code BankAccountAggregate} is the core domain object of this Event Sourcing project.
 *
 * <h2>What makes this Event Sourcing?</h2>
 * Unlike a traditional JPA entity where we UPDATE a row with the new balance, this aggregate:
 * <ol>
 *   <li>Validates the business rule (e.g. "sufficient funds?")</li>
 *   <li>Calls {@code AggregateLifecycle.apply(new SomeEvent(...))} to emit an event</li>
 *   <li>Axon <strong>appends</strong> that event to the event store (never updates)</li>
 *   <li>Axon calls the matching {@code @EventSourcingHandler} to update in-memory state</li>
 * </ol>
 *
 * <p>When an aggregate is loaded for a subsequent command, Axon replays the full
 * event stream from the database through all {@code @EventSourcingHandler} methods in
 * chronological order, reconstructing the current state without ever reading a balance column.
 *
 * <h2>State transitions</h2>
 * <pre>
 *   (none)  ──OpenAccountCommand──► ACTIVE
 *   ACTIVE  ──DepositMoneyCommand──► ACTIVE  (balance increases)
 *   ACTIVE  ──WithdrawMoneyCommand─► ACTIVE  (balance decreases; fails if insufficient funds)
 *   ACTIVE  ──CloseAccountCommand──► CLOSED
 *   CLOSED  ──(any command)─────────► exception (account is closed)
 * </pre>
 *
 * <h2>Command handling vs. Event sourcing handlers</h2>
 * <ul>
 *   <li>{@code @CommandHandler} methods — contain business logic and decide whether to apply events</li>
 *   <li>{@code @EventSourcingHandler} methods — pure state setters, no logic, no side effects</li>
 * </ul>
 * This separation ensures that replaying events only reconstructs state, without re-running
 * business logic or producing side effects.
 */
@Aggregate  // Marks this class as an Axon aggregate — Axon manages its lifecycle
public class BankAccountAggregate {

    /**
     * The aggregate identifier — the primary key that routes commands and ties together
     * all events belonging to this particular bank account in the event store.
     *
     * Axon correlates the {@code @TargetAggregateIdentifier} on the command with this
     * field to load the correct event stream.
     */
    @AggregateIdentifier
    private String accountId;

    /** The account owner's full name — set once on open and never changed. */
    private String ownerName;

    /**
     * The current balance — computed entirely from replaying events.
     * Never stored as a column in the database; only exists in memory.
     */
    private BigDecimal balance;

    /** Whether the account is ACTIVE or CLOSED. */
    private AccountStatus status;

    /**
     * No-arg constructor required by Axon.
     *
     * <p>Axon uses reflection to create a new (empty) aggregate instance before replaying
     * the stored events. This constructor must not contain any business logic — all state
     * is populated exclusively by {@code @EventSourcingHandler} methods.
     */
    @SuppressWarnings("unused")
    protected BankAccountAggregate() {
        // Required by Axon Framework for event sourcing reconstruction — DO NOT add logic here
    }

    // =========================================================================
    //  Command Handlers (write side — business logic lives here)
    // =========================================================================

    /**
     * Handles {@link OpenAccountCommand} — creates a new bank account aggregate.
     *
     * <p>This is a <em>creation command handler</em> (constructor form). Axon calls it
     * when no aggregate with the given ID exists. It validates the input and applies
     * the first event in the aggregate's event stream.
     *
     * @param command the incoming open-account command
     */
    @CommandHandler
    public BankAccountAggregate(OpenAccountCommand command) {
        // Business rule: initial deposit cannot be negative
        if (command.initialDeposit() == null || command.initialDeposit().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Initial deposit cannot be negative, got: " + command.initialDeposit());
        }
        if (command.ownerName() == null || command.ownerName().isBlank()) {
            throw new IllegalArgumentException("Owner name must not be blank");
        }

        // Emit the first event — Axon persists it and calls on(AccountOpenedEvent) below
        AggregateLifecycle.apply(new AccountOpenedEvent(
                command.accountId(),
                command.ownerName(),
                command.initialDeposit(),
                Instant.now()
        ));
    }

    /**
     * Handles {@link DepositMoneyCommand} — deposits money into an active account.
     *
     * <p>Before this method runs, Axon has already:
     * <ol>
     *   <li>Read all stored events for this accountId from the event store</li>
     *   <li>Replayed them through the {@code @EventSourcingHandler} methods</li>
     *   <li>Restored the in-memory state (balance, status, ownerName)</li>
     * </ol>
     * So {@code this.balance} and {@code this.status} reflect the current state.
     *
     * @param command the deposit command
     */
    @CommandHandler
    public void handle(DepositMoneyCommand command) {
        // Business rule: cannot deposit to a closed account
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Cannot deposit to closed account: " + accountId);
        }
        // Business rule: deposit amount must be positive
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Deposit amount must be positive, got: " + command.amount());
        }

        // Compute the new balance and emit the event
        BigDecimal newBalance = balance.add(command.amount());
        AggregateLifecycle.apply(new MoneyDepositedEvent(
                command.accountId(),
                command.amount(),
                newBalance,
                command.description(),
                Instant.now()
        ));
    }

    /**
     * Handles {@link WithdrawMoneyCommand} — withdraws money from an active account.
     *
     * <p>This is where the core Event Sourcing pattern shines: {@code this.balance}
     * was reconstructed from the event log, not from a balance column.
     *
     * @param command the withdrawal command
     */
    @CommandHandler
    public void handle(WithdrawMoneyCommand command) {
        // Business rule: cannot withdraw from a closed account
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Cannot withdraw from closed account: " + accountId);
        }
        // Business rule: withdrawal amount must be positive
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount must be positive, got: " + command.amount());
        }
        // Business rule: no overdraft allowed
        if (command.amount().compareTo(balance) > 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance=" + balance + ", requested=" + command.amount());
        }

        // Compute the new balance and emit the event
        BigDecimal newBalance = balance.subtract(command.amount());
        AggregateLifecycle.apply(new MoneyWithdrawnEvent(
                command.accountId(),
                command.amount(),
                newBalance,
                command.description(),
                Instant.now()
        ));
    }

    /**
     * Handles {@link CloseAccountCommand} — closes an active account.
     *
     * @param command the close-account command
     */
    @CommandHandler
    public void handle(CloseAccountCommand command) {
        // Business rule: cannot close an already-closed account
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException(
                    "Account is already closed: " + accountId);
        }

        AggregateLifecycle.apply(new AccountClosedEvent(
                command.accountId(),
                balance,
                command.reason(),
                Instant.now()
        ));
    }

    // =========================================================================
    //  Event Sourcing Handlers (state reconstruction — NO business logic here)
    // =========================================================================

    /**
     * Applies the {@link AccountOpenedEvent} to set the initial aggregate state.
     *
     * <p><strong>Important:</strong> This method is called twice:
     * <ol>
     *   <li>Immediately after {@code AggregateLifecycle.apply()} during command handling</li>
     *   <li>Each time Axon loads this aggregate from the event store (event replay)</li>
     * </ol>
     * It must contain <em>only</em> state assignments — no events, no external calls.
     */
    @EventSourcingHandler
    public void on(AccountOpenedEvent event) {
        this.accountId = event.accountId();
        this.ownerName = event.ownerName();
        this.balance = event.initialDeposit();
        this.status = AccountStatus.ACTIVE;
    }

    /**
     * Applies the {@link MoneyDepositedEvent} — increases the in-memory balance.
     *
     * <p>The balance is taken directly from the event's {@code balanceAfter} field
     * (which was computed and stored at the time the event was emitted) rather than
     * re-computing it here. This ensures replay consistency even if rounding rules change.
     */
    @EventSourcingHandler
    public void on(MoneyDepositedEvent event) {
        this.balance = event.balanceAfter();
    }

    /**
     * Applies the {@link MoneyWithdrawnEvent} — decreases the in-memory balance.
     */
    @EventSourcingHandler
    public void on(MoneyWithdrawnEvent event) {
        this.balance = event.balanceAfter();
    }

    /**
     * Applies the {@link AccountClosedEvent} — marks the account as CLOSED.
     *
     * <p>After this handler runs, any subsequent command handler will see
     * {@code status == CLOSED} and will throw an exception.
     */
    @EventSourcingHandler
    public void on(AccountClosedEvent event) {
        this.status = AccountStatus.CLOSED;
    }

    // =========================================================================
    //  Accessors (used in tests and read-model projections)
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

    public AccountStatus getStatus() {
        return status;
    }
}
