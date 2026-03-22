package com.example.cqrs.query.api;

/**
 * Query to retrieve a single order by its identifier.
 *
 * <p>In Axon's query model, a <em>query</em> is a simple message object that carries
 * the parameters needed to answer a question about the system state. Queries never
 * mutate state — they only read from the read model (projection).
 *
 * <p>This query is handled by {@code OrderProjection#handle(FindOrderByIdQuery)},
 * which looks up the {@code OrderSummary} JPA entity by primary key.
 *
 * @param orderId the identifier of the order to look up
 */
public record FindOrderByIdQuery(String orderId) {}
