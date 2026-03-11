package com.example.dynamicscheduling.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity that records each individual execution of a scheduled task.
 *
 * <p>Every time a background task fires, a new {@link TaskExecutionLog} row is
 * written to the {@code task_execution_log} table.  This provides a full audit
 * trail that shows:
 * <ul>
 *   <li>When the task ran ({@code firedAt}).</li>
 *   <li>How long it took ({@code durationMs}).</li>
 *   <li>The interval that was in effect at the time ({@code intervalMsSnapshot}).</li>
 *   <li>Whether the execution succeeded or failed ({@code status}).</li>
 *   <li>Any error message produced by a failing execution ({@code errorMessage}).</li>
 * </ul>
 *
 * <p>Flyway migration {@code V1__create_tables.sql} creates this table.
 */
@Entity
@Table(name = "task_execution_log")
public class TaskExecutionLog {

    /** Possible outcomes of a single task execution. */
    public enum Status {
        /** The task completed without throwing an exception. */
        SUCCESS,
        /** The task threw an exception during execution. */
        FAILURE
    }

    /** Auto-generated surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The logical name of the task that fired, e.g. {@code "heartbeat"}.
     * Stored as a plain string rather than a foreign key so that execution
     * history is retained even if the task configuration is later deleted.
     */
    @Column(nullable = false, length = 100)
    private String taskName;

    /**
     * UTC timestamp at which the task execution started.
     * Stored as a microsecond-precision {@code TIMESTAMPTZ} column in PostgreSQL.
     */
    @Column(nullable = false)
    private Instant firedAt;

    /**
     * Wall-clock duration of the task execution in milliseconds.
     * Useful for identifying tasks that are taking longer than expected.
     */
    @Column(nullable = false)
    private long durationMs;

    /**
     * A snapshot of the interval (ms) that was configured when the task fired.
     * Allows audit queries to correlate execution history with schedule changes.
     */
    @Column(nullable = false)
    private long intervalMsSnapshot;

    /**
     * Execution outcome: {@link Status#SUCCESS} or {@link Status#FAILURE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /**
     * Exception message when {@link #status} is {@link Status#FAILURE}.
     * {@code null} for successful executions.
     */
    @Column(length = 2000)
    private String errorMessage;

    // ── Constructors ──────────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected TaskExecutionLog() {}

    /**
     * Convenience constructor for a successful execution.
     *
     * @param taskName          logical name of the task
     * @param firedAt           start timestamp
     * @param durationMs        execution duration in milliseconds
     * @param intervalMsSnapshot interval active during this execution
     */
    public TaskExecutionLog(String taskName, Instant firedAt,
                            long durationMs, long intervalMsSnapshot) {
        this.taskName           = taskName;
        this.firedAt            = firedAt;
        this.durationMs         = durationMs;
        this.intervalMsSnapshot = intervalMsSnapshot;
        this.status             = Status.SUCCESS;
    }

    /**
     * Constructor for a failed execution.
     *
     * @param taskName          logical name of the task
     * @param firedAt           start timestamp
     * @param durationMs        execution duration in milliseconds
     * @param intervalMsSnapshot interval active during this execution
     * @param errorMessage      exception message
     */
    public TaskExecutionLog(String taskName, Instant firedAt,
                            long durationMs, long intervalMsSnapshot,
                            String errorMessage) {
        this.taskName           = taskName;
        this.firedAt            = firedAt;
        this.durationMs         = durationMs;
        this.intervalMsSnapshot = intervalMsSnapshot;
        this.status             = Status.FAILURE;
        this.errorMessage       = errorMessage;
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getTaskName() { return taskName; }

    public Instant getFiredAt() { return firedAt; }

    public long getDurationMs() { return durationMs; }

    public long getIntervalMsSnapshot() { return intervalMsSnapshot; }

    public Status getStatus() { return status; }

    public String getErrorMessage() { return errorMessage; }
}
