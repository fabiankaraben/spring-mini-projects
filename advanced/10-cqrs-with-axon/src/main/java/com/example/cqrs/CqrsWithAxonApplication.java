package com.example.cqrs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Entry point for the CQRS with Axon Framework mini-project.
 *
 * <p>This application demonstrates the Command Query Responsibility Segregation (CQRS)
 * pattern combined with Event Sourcing, using the Axon Framework on top of Spring Boot.
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │                        REST API layer                           │
 *   │  POST /api/orders   PUT /api/orders/{id}/confirm                │
 *   │  PUT /api/orders/{id}/cancel   GET /api/orders/{id}            │
 *   └──────────┬────────────────────────────────┬───────────────────┘
 *              │ Commands                        │ Queries
 *              ▼                                 ▼
 *   ┌──────────────────────┐       ┌────────────────────────────┐
 *   │    Command side       │       │        Query side          │
 *   │                      │       │                            │
 *   │  OrderAggregate      │       │  OrderProjection           │
 *   │  (event sourced)     │       │  (reads OrderSummary JPA)  │
 *   │                      │       │                            │
 *   │  @CommandHandler     │       │  @QueryHandler             │
 *   │  @EventSourcingHandler│      │  @EventHandler             │
 *   └──────────┬───────────┘       └────────────────────────────┘
 *              │ Events (persisted)        ▲ Events (in-memory bus)
 *              ▼                           │
 *   ┌──────────────────────────────────────┴─────────────────────┐
 *   │                     Axon event store (PostgreSQL / JPA)    │
 *   └────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key concepts</h2>
 * <ul>
 *   <li><b>Command</b>  — an intent to change state (e.g. PlaceOrderCommand)</li>
 *   <li><b>Event</b>    — an immutable fact that something happened (e.g. OrderPlacedEvent)</li>
 *   <li><b>Aggregate</b> — domain object that handles commands and emits events; rebuilt
 *                          from its event history on each load (Event Sourcing)</li>
 *   <li><b>Projection</b> — a read-model built by reacting to events; optimised for queries</li>
 *   <li><b>Query</b>    — a request for data that never mutates state</li>
 * </ul>
 */
@SpringBootApplication
@EntityScan(basePackages = {
        "com.example.cqrs",
        "org.axonframework.eventsourcing.eventstore.jpa",
        "org.axonframework.eventhandling.tokenstore.jpa",
        "org.axonframework.modelling.saga.repository.jpa",
        "org.axonframework.messaging.deadletter.jpa"
})
public class CqrsWithAxonApplication {

    public static void main(String[] args) {
        SpringApplication.run(CqrsWithAxonApplication.class, args);
    }
}
