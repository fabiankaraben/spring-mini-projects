package com.example.batchschedulers.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity that records a log entry every time a scheduled batch job is triggered.
 *
 * <p>The scheduler writes one {@code JobAuditLog} row per job launch, capturing:
 * <ul>
 *   <li>Which job was executed ({@code jobName})</li>
 *   <li>The Spring Batch execution ID ({@code jobExecutionId}) for cross-referencing
 *       the native Spring Batch meta-data tables</li>
 *   <li>When the job started and finished ({@code startedAt}, {@code finishedAt})</li>
 *   <li>The final batch status ({@code status}, e.g. "COMPLETED", "FAILED")</li>
 *   <li>How the job was triggered ({@code triggerType}: "SCHEDULED" or "MANUAL")</li>
 * </ul>
 *
 * <p>This provides application-level observability on top of the raw Spring Batch
 * meta-data tables, and can be queried through the REST API.
 *
 * <p>The table {@code job_audit_logs} is created by Flyway migration V2.
 */
@Entity
@Table(name = "job_audit_logs")
public class JobAuditLog {

    /**
     * Auto-generated surrogate primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "job_audit_log_seq")
    @SequenceGenerator(name = "job_audit_log_seq", sequenceName = "job_audit_logs_id_seq", allocationSize = 1)
    private Long id;

    /**
     * Logical name of the batch job, e.g. "priceRefreshJob".
     * Matches the name registered in Spring Batch's {@code Job} bean.
     */
    @Column(name = "job_name", nullable = false)
    private String jobName;

    /**
     * Spring Batch {@code JobExecution} ID, allowing cross-reference with
     * the {@code BATCH_JOB_EXECUTION} table for detailed execution metadata.
     * May be {@code null} if the job failed to launch (e.g. JobInstanceAlreadyCompleteException).
     */
    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    /**
     * Timestamp when the job was launched (before it ran).
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * Timestamp when the job finished (after run returned).
     * {@code null} if the launch itself threw an exception before the job started.
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Final Spring Batch status string, e.g. "COMPLETED", "FAILED", "STOPPED".
     * Set to "LAUNCH_ERROR" if the launch itself threw an exception.
     */
    @Column(nullable = false)
    private String status;

    /**
     * How the job was triggered:
     * <ul>
     *   <li>{@code "SCHEDULED"} – triggered by the {@code @Scheduled} scheduler</li>
     *   <li>{@code "MANUAL"}    – triggered by a {@code POST} to the REST API</li>
     * </ul>
     */
    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    /**
     * Optional free-text message, used to record error details when
     * {@code status} is "LAUNCH_ERROR" or "FAILED".
     */
    @Column(length = 2000)
    private String message;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected JobAuditLog() {}

    /**
     * Convenience constructor for creating a new audit log entry at launch time.
     *
     * @param jobName     the job name
     * @param triggerType "SCHEDULED" or "MANUAL"
     */
    public JobAuditLog(String jobName, String triggerType) {
        this.jobName = jobName;
        this.triggerType = triggerType;
        this.startedAt = LocalDateTime.now();
        this.status = "RUNNING";
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public Long getJobExecutionId() { return jobExecutionId; }
    public void setJobExecutionId(Long jobExecutionId) { this.jobExecutionId = jobExecutionId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return "JobAuditLog{id=" + id + ", jobName='" + jobName + "', status='" + status +
               "', triggerType='" + triggerType + "', startedAt=" + startedAt + "}";
    }
}
