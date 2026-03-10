-- V3__create_product_reports_table.sql
--
-- Creates the product_reports table that stores category-level report snapshots.
-- The reportGenerationJob writes one row per category per run, building a
-- time-series of inventory/pricing statistics.

CREATE SEQUENCE IF NOT EXISTS product_reports_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS product_reports (
    id              BIGINT          NOT NULL DEFAULT nextval('product_reports_id_seq') PRIMARY KEY,
    -- Product category this row summarises (e.g. 'Electronics')
    category        VARCHAR(100)    NOT NULL,
    -- Total number of products in this category at report time
    product_count   INT             NOT NULL,
    -- Sum of stock_quantity across all products in this category
    total_stock     INT             NOT NULL,
    -- Average unit price across all products in this category (rounded to 2 dp)
    average_price   NUMERIC(10, 2)  NOT NULL,
    -- Number of products with low_stock = true in this category
    low_stock_count INT             NOT NULL,
    -- Timestamp when the reportGenerationJob created this snapshot
    generated_at    TIMESTAMP       NOT NULL
);

-- Index on category for per-category report queries
CREATE INDEX IF NOT EXISTS idx_product_reports_category ON product_reports (category);

-- Index on generated_at for time-ordered queries
CREATE INDEX IF NOT EXISTS idx_product_reports_generated_at ON product_reports (generated_at DESC);
