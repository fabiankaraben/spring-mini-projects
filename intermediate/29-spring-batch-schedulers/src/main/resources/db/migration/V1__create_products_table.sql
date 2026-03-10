-- V1__create_products_table.sql
--
-- Creates the products table which is the main domain entity of this mini-project.
-- Each batch job reads from this table and may update it (price refresh, inventory audit).

CREATE SEQUENCE IF NOT EXISTS products_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS products (
    id                 BIGINT          NOT NULL DEFAULT nextval('products_id_seq') PRIMARY KEY,
    name               VARCHAR(255)    NOT NULL,
    category           VARCHAR(100)    NOT NULL,
    price              NUMERIC(10, 2)  NOT NULL,
    stock_quantity     INT             NOT NULL DEFAULT 0,
    -- low_stock is set to TRUE by the inventoryAuditJob when stock_quantity < LOW_STOCK_THRESHOLD (10)
    low_stock          BOOLEAN         NOT NULL DEFAULT FALSE,
    -- last_price_update is set by the priceRefreshJob after updating the price
    last_price_update  TIMESTAMP,
    -- last_audited is set by the inventoryAuditJob after each audit run
    last_audited       TIMESTAMP,
    -- Enforce unique product names so we can look products up by name in tests
    CONSTRAINT uq_products_name UNIQUE (name)
);

-- Index on category to speed up the per-category queries used by the report job
-- and the REST endpoint GET /api/batch/products?category=...
CREATE INDEX IF NOT EXISTS idx_products_category ON products (category);

-- Index on low_stock to speed up the REST endpoint GET /api/batch/products/low-stock
CREATE INDEX IF NOT EXISTS idx_products_low_stock ON products (low_stock);
