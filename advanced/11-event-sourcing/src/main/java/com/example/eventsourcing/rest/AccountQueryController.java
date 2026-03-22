package com.example.eventsourcing.rest;

import com.example.eventsourcing.query.api.FindAccountByIdQuery;
import com.example.eventsourcing.query.api.FindAllAccountsQuery;
import com.example.eventsourcing.query.model.AccountSummary;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for the <em>query side</em> of the bank account Event Sourcing system.
 *
 * <h2>Responsibility</h2>
 * This controller serves read-only requests. It dispatches query objects to Axon's
 * {@code QueryGateway}, which routes them to the matching {@code @QueryHandler} methods
 * in {@code AccountProjection}.
 *
 * <h2>Query flow</h2>
 * <pre>
 *   HTTP GET /api/accounts/{id}
 *     → FindAccountByIdQuery
 *     → QueryGateway
 *     → AccountProjection.handle(FindAccountByIdQuery)
 *     → SELECT from account_summaries table
 *     → HTTP 200 OK with AccountSummary JSON (or 404 if not found)
 * </pre>
 *
 * <h2>Separation from the command controller</h2>
 * Keeping query and command controllers separate follows the CQRS principle.
 * It makes the read path and write path independently scalable and testable.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountQueryController {

    /** Axon's query gateway — dispatches queries to the appropriate query handlers. */
    private final QueryGateway queryGateway;

    public AccountQueryController(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    /**
     * GET /api/accounts
     *
     * <p>Returns all bank account summaries. Supports optional status filtering:
     * <ul>
     *   <li>{@code GET /api/accounts} — all accounts</li>
     *   <li>{@code GET /api/accounts?status=ACTIVE} — only active accounts</li>
     *   <li>{@code GET /api/accounts?status=CLOSED} — only closed accounts</li>
     * </ul>
     *
     * @param status optional status filter
     * @return 200 OK with list of account summaries
     */
    @GetMapping
    public ResponseEntity<List<AccountSummary>> getAllAccounts(
            @RequestParam(required = false) String status) {

        List<AccountSummary> accounts = queryGateway.query(
                new FindAllAccountsQuery(status),
                ResponseTypes.multipleInstancesOf(AccountSummary.class)
        ).join();

        return ResponseEntity.ok(accounts);
    }

    /**
     * GET /api/accounts/{accountId}
     *
     * <p>Returns a single bank account summary by its ID.
     *
     * @param accountId the account ID to look up
     * @return 200 OK with the account summary, or 404 Not Found
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountSummary> getAccountById(@PathVariable String accountId) {
        Optional<AccountSummary> result = queryGateway.query(
                new FindAccountByIdQuery(accountId),
                ResponseTypes.optionalInstanceOf(AccountSummary.class)
        ).join();

        return result.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
