-- ============================================================================
-- V1__create_tables.sql
-- Creates the two core tables for the dynamic-scheduling application:
--   1. task_config         – stores each task's scheduling configuration
--   2. task_execution_log  – stores an audit record for every task execution
--
-- Managed by Flyway; never edit manually.  To alter the schema create a new
-- V2__ migration file instead.
-- ============================================================================

-- ── task_config ───────────────────────────────────────────────────────────────
-- Stores one row per registered dynamic task.  The interval_ms column is the
-- value that can be changed at runtime via the REST API.  Changes here are
-- picked up by the scheduler on the next trigger evaluation cycle.
CREATE TABLE IF NOT EXISTS task_config (
    id          BIGSERIAL    PRIMARY KEY,
    task_name   VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    interval_ms BIGINT       NOT NULL CHECK (interval_ms >= 1000),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Index on task_name because the application always looks up tasks by name
CREATE INDEX IF NOT EXISTS idx_task_config_task_name ON task_config(task_name);

-- ── task_execution_log ────────────────────────────────────────────────────────
-- Append-only audit log.  One row is inserted after each task execution,
-- regardless of success or failure.
CREATE TABLE IF NOT EXISTS task_execution_log (
    id                   BIGSERIAL    PRIMARY KEY,
    task_name            VARCHAR(100) NOT NULL,
    fired_at             TIMESTAMPTZ  NOT NULL,
    duration_ms          BIGINT       NOT NULL,
    interval_ms_snapshot BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('SUCCESS', 'FAILURE')),
    error_message        VARCHAR(2000)
);

-- Index on task_name + fired_at (DESC) to speed up the most common query:
-- "fetch the latest N executions for task X"
CREATE INDEX IF NOT EXISTS idx_exec_log_task_name_fired_at
    ON task_execution_log(task_name, fired_at DESC);
