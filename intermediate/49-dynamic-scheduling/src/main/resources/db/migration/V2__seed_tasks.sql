-- ============================================================================
-- V2__seed_tasks.sql
-- Inserts four demo task configurations that are loaded by the scheduler on
-- first startup.  These showcase different execution frequencies so that the
-- dynamic interval-update feature is immediately observable.
--
-- Tasks can be modified at runtime via the REST API without modifying this
-- file.  To reset to these defaults, delete all rows from task_config and
-- restart the application (Flyway will NOT re-run this migration).
-- ============================================================================

INSERT INTO task_config (task_name, description, interval_ms, enabled) VALUES
    -- Fast heartbeat – fires every 3 seconds; good for demonstrating live interval changes
    ('heartbeat',
     'Lightweight health-check ping. Runs every 3 seconds by default.',
     3000,
     true),

    -- Report generator – fires every 30 seconds; simulates a heavier periodic job
    ('report',
     'Periodic report generation. Aggregates data and produces a summary. Runs every 30 seconds.',
     30000,
     true),

    -- Data cleanup – fires every 60 seconds; simulates a maintenance job
    ('cleanup',
     'Removes stale records from the data store. Runs every 60 seconds.',
     60000,
     true),

    -- Data sync – created in a disabled state so operators can enable it on demand
    ('data-sync',
     'Pulls data from an external source and reconciles with the local store. Disabled by default.',
     15000,
     false)

ON CONFLICT (task_name) DO NOTHING;
-- ON CONFLICT DO NOTHING ensures idempotency: re-running the migration on a
-- database that already has these rows is a no-op (useful in dev/test resets).
