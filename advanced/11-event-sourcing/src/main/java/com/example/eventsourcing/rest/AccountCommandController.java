package com.example.eventsourcing.rest;

import com.example.eventsourcing.command.api.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the <em>command side</em> of the bank account Event Sourcing system.
 *
 * <h2>Responsibility</h2>
 * This controller accepts HTTP requests that represent an intent to change state
 * (open account, deposit, withdraw, close). It translates each request into a
 * command object and dispatches it to Axon's {@code CommandGateway}.
 *
 * <h2>Command flow</h2>
 * <pre>
 *   HTTP POST /api/accounts
 *     → OpenAccountCommand
 *     → CommandGateway
 *     → BankAccountAggregate (constructor @CommandHandler)
 *     → AggregateLifecycle.apply(AccountOpenedEvent)
 *     → Event stored in domain_event_entry table
 *     → AccountProjection.on(AccountOpenedEvent) updates account_summaries table
 *     → HTTP 201 Created
 * </pre>
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountCommandController {

    /** Axon's command gateway — dispatches commands to the appropriate command handlers. */
    private final CommandGateway commandGateway;

    public AccountCommandController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    // =========================================================================
    //  Request body records
    // =========================================================================

    /**
     * Request body for opening a new account.
     * Bean Validation annotations enforce constraints at the HTTP layer
     * before the command even reaches the aggregate.
     */
    record OpenAccountRequest(
            @NotBlank(message = "Owner name must not be blank")
            String ownerName,

            @NotNull(message = "Initial deposit is required")
            @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
            BigDecimal initialDeposit
    ) {}

    /**
     * Request body for depositing money.
     */
    record DepositRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Deposit amount must be positive")
            BigDecimal amount,

            String description
    ) {}

    /**
     * Request body for withdrawing money.
     */
    record WithdrawRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Withdrawal amount must be positive")
            BigDecimal amount,

            String description
    ) {}

    /**
     * Request body for closing an account (reason is optional).
     */
    record CloseAccountRequest(String reason) {}

    // =========================================================================
    //  Command endpoints
    // =========================================================================

    /**
     * POST /api/accounts
     *
     * <p>Opens a new bank account. Generates a UUID as the account ID and dispatches
     * an {@link OpenAccountCommand} to the Axon command bus.
     *
     * @param request the open-account request body
     * @return 201 Created with the new account ID
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> openAccount(
            @Valid @RequestBody OpenAccountRequest request) {

        // Generate a unique account ID — in production this might come from a sequence or external system
        String accountId = UUID.randomUUID().toString();

        commandGateway.sendAndWait(new OpenAccountCommand(
                accountId,
                request.ownerName(),
                request.initialDeposit()
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "accountId", accountId,
                        "message", "Account opened successfully"
                ));
    }

    /**
     * POST /api/accounts/{accountId}/deposits
     *
     * <p>Deposits money into an existing account. The aggregate validates that
     * the account is ACTIVE before emitting a {@code MoneyDepositedEvent}.
     *
     * @param accountId the target account ID
     * @param request   the deposit request body
     * @return 200 OK on success
     */
    @PostMapping("/{accountId}/deposits")
    public ResponseEntity<Map<String, String>> deposit(
            @PathVariable String accountId,
            @Valid @RequestBody DepositRequest request) {

        commandGateway.sendAndWait(new DepositMoneyCommand(
                accountId,
                request.amount(),
                request.description()
        ));

        return ResponseEntity.ok(Map.of("message", "Deposit successful"));
    }

    /**
     * POST /api/accounts/{accountId}/withdrawals
     *
     * <p>Withdraws money from an existing account. The aggregate validates:
     * <ul>
     *   <li>The account is ACTIVE</li>
     *   <li>The balance is sufficient (no overdraft)</li>
     * </ul>
     *
     * @param accountId the target account ID
     * @param request   the withdrawal request body
     * @return 200 OK on success, or 409 Conflict if insufficient funds
     */
    @PostMapping("/{accountId}/withdrawals")
    public ResponseEntity<Map<String, String>> withdraw(
            @PathVariable String accountId,
            @Valid @RequestBody WithdrawRequest request) {

        commandGateway.sendAndWait(new WithdrawMoneyCommand(
                accountId,
                request.amount(),
                request.description()
        ));

        return ResponseEntity.ok(Map.of("message", "Withdrawal successful"));
    }

    /**
     * DELETE /api/accounts/{accountId}
     *
     * <p>Closes an existing bank account. Uses HTTP DELETE because closing an account
     * is a destructive lifecycle operation.
     *
     * @param accountId the account to close
     * @param request   optional request body with a reason for closure
     * @return 200 OK on success
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Map<String, String>> closeAccount(
            @PathVariable String accountId,
            @RequestBody(required = false) CloseAccountRequest request) {

        String reason = (request != null) ? request.reason() : null;
        commandGateway.sendAndWait(new CloseAccountCommand(accountId, reason));

        return ResponseEntity.ok(Map.of("message", "Account closed successfully"));
    }
}
