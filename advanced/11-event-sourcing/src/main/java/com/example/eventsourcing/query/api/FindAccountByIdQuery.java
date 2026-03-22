package com.example.eventsourcing.query.api;

/**
 * Query to retrieve a single bank account summary by its ID.
 *
 * <h2>CQRS Query side</h2>
 * Queries are the read side of CQRS. They do not change state — they only read from
 * the projection (read model). Axon routes this query to the matching
 * {@code @QueryHandler} method in {@code AccountProjection}.
 *
 * @param accountId the unique identifier of the account to retrieve
 */
public record FindAccountByIdQuery(String accountId) {}
