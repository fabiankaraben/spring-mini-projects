-- ============================================================================
-- V2__create_job_audit_log.sql
--
-- Creates the custom job_audit_log table used to record every Quartz job
-- execution with its outcome (SUCCESS or FAILED) and duration.
--
-- This table is not part of Quartz itself – it is our application-level
-- audit log that gives operators visibility into job execution history.
-- Each of the three job types (SampleLoggingJob, DataCleanupJob,
-- ReportGenerationJob) writes one row here after every execution.
--
-- The JPA entity com.example.quartzscheduler.model.JobAuditLog maps to
-- this table.
-- ============================================================================

CREATE TABLE IF NOT EXISTS job_audit_log (
    -- Auto-incrementing surrogate primary key
    id           BIGSERIAL    PRIMARY KEY,

    -- Quartz job identity (mirrors QRTZ_JOB_DETAILS.JOB_NAME / JOB_GROUP)
    job_name     VARCHAR(200) NOT NULL,
    job_group    VARCHAR(200) NOT NULL,

    -- Fully-qualified class name of the executed job (for quick debugging)
    job_class    VARCHAR(500),

    -- Timestamps of execution start and end (stored in UTC)
    fired_at     TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,

    -- Wall-clock execution time in milliseconds (finished_at - fired_at)
    duration_ms  BIGINT,

    -- Execution outcome: 'SUCCESS' or 'FAILED'
    status       VARCHAR(20)  NOT NULL,

    -- Short summary message for successful runs or exception message for failures
    message      VARCHAR(2000)
);

-- Index on job_name for fast filtering by job (used by AuditLogController)
CREATE INDEX IF NOT EXISTS idx_audit_job_name
    ON job_audit_log (job_name);

-- Composite index on job_name + job_group (used by the group+name filter)
CREATE INDEX IF NOT EXISTS idx_audit_job_name_group
    ON job_audit_log (job_name, job_group);

-- Index on fired_at DESC for the "most recent first" ordering used in all queries
CREATE INDEX IF NOT EXISTS idx_audit_fired_at
    ON job_audit_log (fired_at DESC);
