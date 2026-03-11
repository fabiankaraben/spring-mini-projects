package com.example.dynamicscheduling.model;

import jakarta.persistence.*;

/**
 * JPA entity that persists a named task's scheduling configuration to PostgreSQL.
 *
 * <p>Each row represents one logical background task registered with the
 * dynamic scheduler.  The {@code intervalMs} column holds the current execution
 * interval in milliseconds and is the value that can be changed at runtime via
 * the REST API.  The {@code enabled} flag lets operators pause a task without
 * removing it from the database.
 *
 * <p>Flyway migration {@code V1__create_tables.sql} creates this table on first
 * startup, so Hibernate only validates the schema (ddl-auto=validate).
 */
@Entity
@Table(name = "task_config")
public class TaskConfig {

    /** Primary key – auto-generated surrogate key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique logical name of the task, e.g. {@code "heartbeat"}, {@code "report"}.
     * Used as the key when looking up tasks in the registry and REST endpoints.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String taskName;

    /**
     * Human-readable description of what this task does.
     * Optional – shown in API responses for documentation purposes.
     */
    @Column(length = 500)
    private String description;

    /**
     * Execution interval in milliseconds.
     * The scheduler reloads this value on the next execution cycle whenever
     * it detects a change via the REST API.
     *
     * <p>Minimum enforced by validation: 1000 ms (1 second).
     */
    @Column(nullable = false)
    private long intervalMs;

    /**
     * Whether the task should execute.  When set to {@code false} the task
     * continues to be tracked but its {@link java.util.concurrent.ScheduledFuture}
     * is cancelled until re-enabled.
     */
    @Column(nullable = false)
    private boolean enabled;

    // ── Constructors ──────────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected TaskConfig() {}

    /**
     * Full constructor used when creating a new task configuration record.
     *
     * @param taskName   unique logical name of the task
     * @param description human-readable description
     * @param intervalMs  execution interval in milliseconds (≥ 1000)
     * @param enabled     whether the task should execute
     */
    public TaskConfig(String taskName, String description, long intervalMs, boolean enabled) {
        this.taskName    = taskName;
        this.description = description;
        this.intervalMs  = intervalMs;
        this.enabled     = enabled;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "TaskConfig{id=" + id +
               ", taskName='" + taskName + '\'' +
               ", intervalMs=" + intervalMs +
               ", enabled=" + enabled + '}';
    }
}
