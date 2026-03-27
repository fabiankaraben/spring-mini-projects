-- ============================================================================
-- Tenant schema DDL — applied to every tenant's PostgreSQL schema
--
-- This script is executed by TenantSchemaInitializer at application startup,
-- once per tenant schema (e.g., tenant_alpha, tenant_beta).
--
-- The search_path is set to the target tenant schema before this script runs,
-- so all CREATE TABLE statements below create tables in the correct schema
-- without needing fully-qualified names (schema.table).
--
-- Design notes:
--   - All tables use IF NOT EXISTS so re-runs are safe (idempotent).
--   - Each tenant has its own independent sequences for primary keys —
--     a product with id=1 in tenant_alpha is a completely different row
--     from a product with id=1 in tenant_beta.
--   - There are NO tenant discriminator columns: isolation is at the schema
--     level, not the row level.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- products table
--
-- Stores the products catalog for one tenant.
-- Columns:
--   id             — surrogate primary key (auto-increment via BIGSERIAL)
--   name           — human-readable product name, required
--   description    — optional text description
--   price          — decimal price with 2 decimal places (e.g., 9.99)
--   stock_quantity — number of units currently in stock
--   created_at     — UTC timestamp of record creation (immutable)
--   updated_at     — UTC timestamp of the last update
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id             BIGSERIAL    PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    price          NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER      NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index on name for efficient keyword search (ILIKE / LIKE queries)
CREATE INDEX IF NOT EXISTS idx_products_name ON products (name);
