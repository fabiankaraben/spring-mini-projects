package com.example.quartzscheduler.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity that records one execution event for every Quartz job that runs.
 *
 * <p>Every time a Quartz job fires, the corresponding {@code execute()} method
 * persists a {@code JobAuditLog} row via {@link com.example.quartzscheduler.repository.JobAuditLogRepository}.
 * This gives operators a queryable history of all job executions, including
 * their durations and any failure messages.
 *
 * <p>The backing table ({@code job_audit_log}) is created by Flyway migration
 * {@code V2__create_job_audit_log.sql}.
 */
@Entity
@Table(name = "job_audit_log")
public class JobAuditLog {

    /** Auto-generated surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Quartz job name (maps to {@code QRTZ_JOB_DETAILS.JOB_NAME}).
     * Together with {@link #jobGroup} it uniquely identifies a job definition.
     */
    @Column(name = "job_name", nullable = false)
    private String jobName;

    /**
     * Quartz job group (maps to {@code QRTZ_JOB_DETAILS.JOB_GROUP}).
     * Used to namespace related jobs – e.g. "MAINTENANCE", "REPORTING".
     */
    @Column(name = "job_group", nullable = false)
    private String jobGroup;

    /**
     * Quartz job class that was executed (fully-qualified class name).
     * Useful for debugging when multiple job types share the same name pattern.
     */
    @Column(name = "job_class")
    private String jobClass;

    /** UTC instant at which the job started executing. */
    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    /** UTC instant at which the job finished (success or failure). */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /**
     * Wall-clock duration in milliseconds computed as
     * {@code finishedAt - firedAt}. {@code null} while the job is still running.
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Execution outcome: {@code SUCCESS} or {@code FAILED}.
     * Stored as a plain VARCHAR so it is readable without any enum mapping.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Optional free-text message produced by the job.
     * For successful executions this is a short summary; for failures it
     * contains the exception message to aid diagnostics.
     */
    @Column(name = "message", length = 2000)
    private String message;

    /** Required by JPA – do not use directly. */
    protected JobAuditLog() {}

    /**
     * Creates a new audit-log entry with all mandatory fields.
     *
     * @param jobName   Quartz job name
     * @param jobGroup  Quartz job group
     * @param jobClass  fully-qualified class name of the executed job
     * @param firedAt   UTC instant the job started
     * @param status    {@code "SUCCESS"} or {@code "FAILED"}
     */
    public JobAuditLog(String jobName, String jobGroup, String jobClass,
                       Instant firedAt, String status) {
        this.jobName  = jobName;
        this.jobGroup = jobGroup;
        this.jobClass = jobClass;
        this.firedAt  = firedAt;
        this.status   = status;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }

    public String getJobClass() { return jobClass; }
    public void setJobClass(String jobClass) { this.jobClass = jobClass; }

    public Instant getFiredAt() { return firedAt; }
    public void setFiredAt(Instant firedAt) { this.firedAt = firedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
