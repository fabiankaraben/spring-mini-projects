-- V2__create_job_audit_logs_table.sql
--
-- Creates the job_audit_logs table that records every batch job execution.
-- The BatchJobScheduler writes one row per job launch (SCHEDULED or MANUAL).

CREATE SEQUENCE IF NOT EXISTS job_audit_logs_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS job_audit_logs (
    id                BIGINT        NOT NULL DEFAULT nextval('job_audit_logs_id_seq') PRIMARY KEY,
    -- Logical job name matching the Spring Batch Job bean name
    job_name          VARCHAR(100)  NOT NULL,
    -- Foreign key reference to Spring Batch's BATCH_JOB_EXECUTION table (informational)
    job_execution_id  BIGINT,
    -- Timestamp when the scheduler/controller triggered the job
    started_at        TIMESTAMP     NOT NULL,
    -- Timestamp when the job finished (null if still running or failed to launch)
    finished_at       TIMESTAMP,
    -- Final batch status: RUNNING, COMPLETED, FAILED, LAUNCH_ERROR, etc.
    status            VARCHAR(50)   NOT NULL,
    -- How the job was triggered: SCHEDULED or MANUAL
    trigger_type      VARCHAR(20)   NOT NULL,
    -- Optional error message for LAUNCH_ERROR or FAILED statuses
    message           VARCHAR(2000)
);

-- Index on job_name for the per-job audit log queries
CREATE INDEX IF NOT EXISTS idx_job_audit_logs_job_name ON job_audit_logs (job_name);

-- Index on started_at for time-ordered queries
CREATE INDEX IF NOT EXISTS idx_job_audit_logs_started_at ON job_audit_logs (started_at DESC);
