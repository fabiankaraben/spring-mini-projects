package com.example.eventsourcing.query.api;

/**
 * Query to retrieve all bank account summaries, optionally filtered by status.
 *
 * <p>Axon routes this query to the {@code @QueryHandler} in {@code AccountProjection}
 * that accepts a {@code FindAllAccountsQuery} parameter.
 *
 * @param statusFilter optional status filter ("ACTIVE", "CLOSED", or {@code null} for all)
 */
public record FindAllAccountsQuery(String statusFilter) {}
