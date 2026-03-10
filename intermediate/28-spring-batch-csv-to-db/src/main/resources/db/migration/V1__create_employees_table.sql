-- V1__create_employees_table.sql
--
-- Creates the main application table that stores imported employee records.
--
-- This migration is managed by Flyway and runs automatically on startup when
-- the table does not yet exist. The JPA entity `Employee` maps to this table.

CREATE TABLE IF NOT EXISTS employees (
    -- Surrogate primary key, auto-incremented by the PostgreSQL sequence
    id          BIGSERIAL PRIMARY KEY,

    -- Employee name fields; both are required (NOT NULL)
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,

    -- Email must be unique across all rows; used as a natural business key
    email       VARCHAR(200) NOT NULL UNIQUE,

    -- The department/team the employee belongs to (e.g. "Engineering", "HR")
    department  VARCHAR(100) NOT NULL,

    -- Gross annual salary; NUMERIC(12,2) supports values up to 9,999,999,999.99
    salary      NUMERIC(12, 2) NOT NULL,

    -- Date the employee was hired; stored as a date (no time component)
    hire_date   DATE NOT NULL
);

-- Index to speed up the common query pattern: filter employees by department
CREATE INDEX IF NOT EXISTS idx_employees_department ON employees (department);
