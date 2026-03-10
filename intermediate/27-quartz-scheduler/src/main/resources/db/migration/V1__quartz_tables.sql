-- ============================================================================
-- V1__quartz_tables.sql
--
-- Creates the Quartz JDBC job-store tables for PostgreSQL.
--
-- These tables are the backbone of Quartz's "JDBC job store" – they replace the
-- in-memory store (RAMJobStore) with a durable, database-backed store.  All job
-- definitions, trigger schedules, execution state, and calendar data are kept
-- here, which means:
--   • Jobs survive application restarts.
--   • Multiple application nodes can share the same job store (clustering).
--   • Job history and misfired triggers are tracked persistently.
--
-- Table overview:
--   QRTZ_JOB_DETAILS      – one row per registered job class + data map
--   QRTZ_TRIGGERS         – one row per trigger (any type)
--   QRTZ_SIMPLE_TRIGGERS  – extension for SimpleTrigger-specific columns
--   QRTZ_CRON_TRIGGERS    – extension for CronTrigger-specific columns (cron expr)
--   QRTZ_SIMPROP_TRIGGERS – extension for CalendarIntervalTrigger / DailyTimeIntervalTrigger
--   QRTZ_BLOB_TRIGGERS    – fallback for trigger types stored as BLOBs
--   QRTZ_CALENDARS        – named calendar objects used to exclude dates from schedules
--   QRTZ_PAUSED_TRIGGER_GRPS – tracks which trigger groups are currently paused
--   QRTZ_FIRED_TRIGGERS   – tracks triggers that are currently firing (in-flight)
--   QRTZ_SCHEDULER_STATE  – heartbeat/health table used in clustered deployments
--   QRTZ_LOCKS            – pessimistic locking table used for cluster coordination
--
-- Source: official Quartz distribution tables_postgres.sql (2.5.x)
-- ============================================================================

-- ── QRTZ_JOB_DETAILS ─────────────────────────────────────────────────────────
-- Stores one row for every distinct job definition registered with the scheduler.
-- The JOB_DATA column holds the serialised JobDataMap (binary BLOB).
CREATE TABLE IF NOT EXISTS QRTZ_JOB_DETAILS (
    SCHED_NAME        VARCHAR(120) NOT NULL,  -- scheduler instance name (from instanceName property)
    JOB_NAME          VARCHAR(200) NOT NULL,  -- unique job name within its group
    JOB_GROUP         VARCHAR(200) NOT NULL,  -- logical group (e.g. "MAINTENANCE")
    DESCRIPTION       VARCHAR(250),           -- optional human-readable description
    JOB_CLASS_NAME    VARCHAR(250) NOT NULL,  -- fully-qualified Java class name of the job
    IS_DURABLE        BOOLEAN      NOT NULL,  -- true = keep job even when no triggers reference it
    IS_NONCONCURRENT  BOOLEAN      NOT NULL,  -- true = @DisallowConcurrentExecution is present
    IS_UPDATE_DATA    BOOLEAN      NOT NULL,  -- true = @PersistJobDataAfterExecution is present
    REQUESTS_RECOVERY BOOLEAN      NOT NULL,  -- true = re-fire on scheduler recovery after crash
    JOB_DATA          BYTEA,                  -- serialised JobDataMap (may be null if empty)
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

-- ── QRTZ_TRIGGERS ─────────────────────────────────────────────────────────────
-- Parent table for all trigger types. Each trigger has one row here plus one
-- row in a type-specific extension table (CRON, SIMPLE, SIMPROP, or BLOB).
CREATE TABLE IF NOT EXISTS QRTZ_TRIGGERS (
    SCHED_NAME         VARCHAR(120) NOT NULL,
    TRIGGER_NAME       VARCHAR(200) NOT NULL,
    TRIGGER_GROUP      VARCHAR(200) NOT NULL,
    JOB_NAME           VARCHAR(200) NOT NULL,  -- FK → QRTZ_JOB_DETAILS
    JOB_GROUP          VARCHAR(200) NOT NULL,
    DESCRIPTION        VARCHAR(250),
    NEXT_FIRE_TIME     BIGINT,                  -- epoch-millis of next scheduled fire
    PREV_FIRE_TIME     BIGINT,                  -- epoch-millis of most recent fire (-1 if never)
    PRIORITY           INTEGER,                 -- trigger priority (higher = picked first)
    TRIGGER_STATE      VARCHAR(16)  NOT NULL,   -- WAITING, ACQUIRED, EXECUTING, PAUSED, BLOCKED, ERROR, COMPLETE
    TRIGGER_TYPE       VARCHAR(8)   NOT NULL,   -- CRON, SIMPLE, SIMPROP, BLOB
    START_TIME         BIGINT       NOT NULL,   -- earliest time the trigger may fire
    END_TIME           BIGINT,                  -- latest time the trigger may fire (null = no end)
    CALENDAR_NAME      VARCHAR(200),            -- named calendar to exclude dates (optional)
    MISFIRE_INSTR      SMALLINT,                -- what to do when a trigger misfires (missed its window)
    JOB_DATA           BYTEA,                   -- trigger-level job data (overrides job-level data map)
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
        REFERENCES QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP)
);

-- ── QRTZ_SIMPLE_TRIGGERS ──────────────────────────────────────────────────────
-- Extension table for SimpleTrigger (fire N times every X milliseconds).
CREATE TABLE IF NOT EXISTS QRTZ_SIMPLE_TRIGGERS (
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(200) NOT NULL,
    TRIGGER_GROUP   VARCHAR(200) NOT NULL,
    REPEAT_COUNT    BIGINT       NOT NULL,  -- how many times to repeat (-1 = indefinitely)
    REPEAT_INTERVAL BIGINT       NOT NULL,  -- milliseconds between repeats
    TIMES_TRIGGERED BIGINT       NOT NULL,  -- how many times the trigger has fired so far
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

-- ── QRTZ_CRON_TRIGGERS ────────────────────────────────────────────────────────
-- Extension table for CronTrigger (fire on a cron schedule).
-- This is the table used by all the jobs in this mini-project.
CREATE TABLE IF NOT EXISTS QRTZ_CRON_TRIGGERS (
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(200) NOT NULL,
    TRIGGER_GROUP   VARCHAR(200) NOT NULL,
    CRON_EXPRESSION VARCHAR(120) NOT NULL,  -- the Quartz cron expression (6–7 fields)
    TIME_ZONE_ID    VARCHAR(80),             -- time zone for the cron evaluation (e.g. "UTC")
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

-- ── QRTZ_SIMPROP_TRIGGERS ─────────────────────────────────────────────────────
-- Extension table for CalendarIntervalTrigger and DailyTimeIntervalTrigger.
CREATE TABLE IF NOT EXISTS QRTZ_SIMPROP_TRIGGERS (
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_NAME  VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    STR_PROP_1    VARCHAR(512),
    STR_PROP_2    VARCHAR(512),
    STR_PROP_3    VARCHAR(512),
    INT_PROP_1    INTEGER,
    INT_PROP_2    INTEGER,
    LONG_PROP_1   BIGINT,
    LONG_PROP_2   BIGINT,
    DEC_PROP_1    NUMERIC(13,4),
    DEC_PROP_2    NUMERIC(13,4),
    BOOL_PROP_1   BOOLEAN,
    BOOL_PROP_2   BOOLEAN,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

-- ── QRTZ_BLOB_TRIGGERS ───────────────────────────────────────────────────────
-- Fallback for custom trigger types that are serialised as a binary BLOB.
CREATE TABLE IF NOT EXISTS QRTZ_BLOB_TRIGGERS (
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_NAME  VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    BLOB_DATA     BYTEA,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
);

-- ── QRTZ_CALENDARS ───────────────────────────────────────────────────────────
-- Stores named calendar objects (e.g. "holidays") that can be applied to
-- triggers to skip certain dates.
CREATE TABLE IF NOT EXISTS QRTZ_CALENDARS (
    SCHED_NAME    VARCHAR(120) NOT NULL,
    CALENDAR_NAME VARCHAR(200) NOT NULL,  -- user-defined calendar name
    CALENDAR      BYTEA        NOT NULL,  -- serialised org.quartz.Calendar object
    PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
);

-- ── QRTZ_PAUSED_TRIGGER_GRPS ─────────────────────────────────────────────────
-- Records which trigger groups are currently paused so the state survives restarts.
CREATE TABLE IF NOT EXISTS QRTZ_PAUSED_TRIGGER_GRPS (
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
);

-- ── QRTZ_FIRED_TRIGGERS ───────────────────────────────────────────────────────
-- Tracks triggers that have been "acquired" by a node and are currently firing.
-- Used to detect misfired or in-flight jobs after a crash.
CREATE TABLE IF NOT EXISTS QRTZ_FIRED_TRIGGERS (
    SCHED_NAME        VARCHAR(120) NOT NULL,
    ENTRY_ID          VARCHAR(95)  NOT NULL,   -- unique ID for this firing event
    TRIGGER_NAME      VARCHAR(200) NOT NULL,
    TRIGGER_GROUP     VARCHAR(200) NOT NULL,
    INSTANCE_NAME     VARCHAR(200) NOT NULL,   -- scheduler instanceId that acquired the trigger
    FIRED_TIME        BIGINT       NOT NULL,   -- epoch-millis when the trigger was acquired
    SCHED_TIME        BIGINT       NOT NULL,   -- epoch-millis of the originally scheduled fire
    PRIORITY          INTEGER      NOT NULL,
    STATE             VARCHAR(16)  NOT NULL,   -- ACQUIRED, EXECUTING, BLOCKED, PAUSED_BLOCKED, etc.
    JOB_NAME          VARCHAR(200),
    JOB_GROUP         VARCHAR(200),
    IS_NONCONCURRENT  BOOLEAN,
    REQUESTS_RECOVERY BOOLEAN,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID)
);

-- ── QRTZ_SCHEDULER_STATE ──────────────────────────────────────────────────────
-- Each scheduler node writes a heartbeat here so other nodes can detect failures.
-- Not critical for single-node deployments but required by the JDBC job store.
CREATE TABLE IF NOT EXISTS QRTZ_SCHEDULER_STATE (
    SCHED_NAME        VARCHAR(120) NOT NULL,
    INSTANCE_NAME     VARCHAR(200) NOT NULL,  -- this node's instanceId
    LAST_CHECKIN_TIME BIGINT       NOT NULL,  -- epoch-millis of most recent heartbeat
    CHECKIN_INTERVAL  BIGINT       NOT NULL,  -- expected interval between heartbeats (ms)
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
);

-- ── QRTZ_LOCKS ───────────────────────────────────────────────────────────────
-- Pessimistic-locking table used by the JDBC job store to serialise critical
-- sections across clustered nodes (e.g. trigger acquisition).
-- Pre-populated with the five lock names that Quartz uses internally.
CREATE TABLE IF NOT EXISTS QRTZ_LOCKS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME  VARCHAR(40)  NOT NULL,  -- one of: TRIGGER_ACCESS, JOB_ACCESS, CALENDAR_ACCESS, STATE_ACCESS, MISFIRE_ACCESS
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
);

-- Pre-populate the locks table with the five lock names Quartz expects.
-- INSERT ... ON CONFLICT DO NOTHING is idempotent if this migration is re-run.
INSERT INTO QRTZ_LOCKS (SCHED_NAME, LOCK_NAME) VALUES
    ('QuartzScheduler', 'TRIGGER_ACCESS'),
    ('QuartzScheduler', 'JOB_ACCESS'),
    ('QuartzScheduler', 'CALENDAR_ACCESS'),
    ('QuartzScheduler', 'STATE_ACCESS'),
    ('QuartzScheduler', 'MISFIRE_ACCESS')
ON CONFLICT DO NOTHING;

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- These indexes mirror those in the official Quartz distribution scripts.
-- They are critical for performance in production – the job store issues
-- queries on NEXT_FIRE_TIME and TRIGGER_STATE very frequently.

CREATE INDEX IF NOT EXISTS IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS (SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_J_GRP           ON QRTZ_JOB_DETAILS (SCHED_NAME, JOB_GROUP);

CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_J             ON QRTZ_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_JG            ON QRTZ_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_C             ON QRTZ_TRIGGERS (SCHED_NAME, CALENDAR_NAME);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_G             ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_STATE         ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_N_STATE       ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_N_G_STATE     ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS (SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_NFT_ST        ON QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_NFT_MISFIRE   ON QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_GROUP, TRIGGER_STATE);

CREATE INDEX IF NOT EXISTS IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_FT_J_G           ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_FT_JG            ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_FT_T_G           ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
CREATE INDEX IF NOT EXISTS IDX_QRTZ_FT_TG            ON QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);
