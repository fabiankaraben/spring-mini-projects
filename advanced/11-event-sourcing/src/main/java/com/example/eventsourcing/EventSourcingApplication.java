package com.example.eventsourcing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Entry point for the Event Sourcing mini-project.
 *
 * <h2>What is Event Sourcing?</h2>
 * Traditional applications store the *current state* of a domain object in a database row.
 * When the state changes, the row is overwritten — history is lost.
 *
 * <p>Event Sourcing takes a different approach: instead of storing the current state,
 * we store the *sequence of events* that led to that state. The current state is
 * derived on demand by replaying the event history from the beginning (or from the
 * last snapshot).
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li><strong>Full audit trail</strong> — every change is recorded as an immutable event</li>
 *   <li><strong>Temporal queries</strong> — reconstruct state at any past point in time</li>
 *   <li><strong>Debugging</strong> — replay events to reproduce bugs</li>
 *   <li><strong>Event-driven integration</strong> — other services can consume the event stream</li>
 *   <li><strong>No UPDATE/DELETE</strong> — the event store is append-only</li>
 * </ul>
 *
 * <h2>Project structure</h2>
 * <pre>
 *   command/
 *     api/          — command objects and domain event objects
 *     aggregate/    — BankAccountAggregate (domain logic + event sourcing handlers)
 *   query/
 *     api/          — query objects
 *     handler/      — AccountProjection (event handler updating the read model)
 *     model/        — AccountSummary (JPA entity for the read model)
 *   rest/           — REST controllers and global exception handler
 *   config/         — Axon Framework infrastructure configuration
 * </pre>
 *
 * <h2>Technology stack</h2>
 * <ul>
 *   <li>Spring Boot 3 — application framework</li>
 *   <li>Axon Framework 4 — event sourcing + CQRS infrastructure</li>
 *   <li>PostgreSQL — event store + read model persistence</li>
 *   <li>Spring Data JPA + Hibernate — ORM for the read model</li>
 * </ul>
 */
@SpringBootApplication
@EntityScan(basePackages = {
        "com.example.eventsourcing",
        "org.axonframework.eventsourcing.eventstore.jpa",
        "org.axonframework.eventhandling.tokenstore.jpa",
        "org.axonframework.modelling.saga.repository.jpa",
        "org.axonframework.messaging.deadletter.jpa"
})
public class EventSourcingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventSourcingApplication.class, args);
    }
}
