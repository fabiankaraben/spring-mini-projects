package com.example.autoconfiguration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity that persists a record for every greeting produced by the application.
 *
 * <p>This entity serves two purposes:
 * <ol>
 *   <li>It demonstrates the auto-configured {@link com.example.autoconfiguration.starter.GreetingService}
 *       working alongside standard Spring Boot JPA infrastructure.</li>
 *   <li>It provides a queryable history of all greetings so callers can retrieve
 *       past results via the REST API.</li>
 * </ol>
 *
 * <p><b>Table structure:</b>
 * <pre>
 *   greeting_logs
 *   ┌──────────────┬──────────────────────────────────────────┐
 *   │ id           │ BIGSERIAL PRIMARY KEY                    │
 *   │ name         │ VARCHAR(255) NOT NULL                    │
 *   │ message      │ VARCHAR(500) NOT NULL (greeting result)  │
 *   │ created_at   │ TIMESTAMP WITH TIME ZONE NOT NULL        │
 *   └──────────────┴──────────────────────────────────────────┘
 * </pre>
 */
@Entity
@Table(name = "greeting_logs")
public class GreetingLog {

    /**
     * Auto-generated primary key. Uses the database's IDENTITY/SERIAL strategy
     * so Hibernate lets the database assign the value.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The name that was greeted. Stored so we can filter by name later.
     */
    @Column(nullable = false)
    private String name;

    /**
     * The full greeting message produced by {@link com.example.autoconfiguration.starter.GreetingService#greet(String)}.
     * Length is 500 to accommodate custom prefixes/suffixes.
     */
    @Column(nullable = false, length = 500)
    private String message;

    /**
     * The UTC timestamp when this greeting was created.
     * Set manually in the service layer (not using JPA auditing here to keep
     * the auto-configuration focus clear and not conflate two concepts).
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * JPA requires a no-argument constructor. Protected visibility prevents
     * accidental instantiation without setting required fields.
     */
    protected GreetingLog() {
    }

    /**
     * Creates a new greeting log record with all required fields.
     *
     * @param name      the name that was greeted
     * @param message   the full greeting message
     * @param createdAt the time the greeting was produced
     */
    public GreetingLog(String name, String message, Instant createdAt) {
        this.name = name;
        this.message = message;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters — no setters for immutable fields (id, createdAt) to enforce
    // correct usage: these values should only be set once at creation time.
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
