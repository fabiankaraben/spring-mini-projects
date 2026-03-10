-- ============================================================
-- Migration V1: Create the products table
-- ============================================================
-- Flyway executes this script exactly once, records it in the
-- flyway_schema_history table, and never runs it again.
-- Naming convention: V<version>__<description>.sql
-- ============================================================

-- products table stores the product catalog.
-- Each row corresponds to one Product entity in Java.
CREATE TABLE IF NOT EXISTS products (
    -- BIGSERIAL is PostgreSQL's auto-incrementing 64-bit integer type.
    -- Spring Data R2DBC reads the generated value and populates Product.id
    -- after every INSERT.
    id              BIGSERIAL       PRIMARY KEY,

    -- Product display name — NOT NULL enforced at the database level as well
    -- as at the application level via @NotBlank validation.
    name            VARCHAR(150)    NOT NULL,

    -- Longer product description — TEXT allows unlimited length in PostgreSQL.
    description     TEXT            NOT NULL,

    -- NUMERIC(12, 2) stores exact decimal values with up to 12 digits total
    -- and exactly 2 decimal places (e.g., 9999999999.99). Using NUMERIC instead
    -- of FLOAT avoids binary floating-point rounding errors in monetary values.
    price           NUMERIC(12, 2)  NOT NULL CHECK (price >= 0.01),

    -- Category tag for grouping/filtering products.
    category        VARCHAR(100)    NOT NULL,

    -- Available stock. CHECK constraint mirrors the @Min(0) validation in the DTO.
    stock_quantity  INTEGER         NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),

    -- Soft-delete / visibility flag. FALSE means the product is hidden from
    -- the public storefront but retained in the database.
    active          BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit timestamps — populated by Spring Data's @CreatedDate / @LastModifiedDate.
    -- TIMESTAMPTZ stores the instant in UTC and preserves timezone information.
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────────────────────────

-- Index on category to speed up findByCategory queries (most common filter).
CREATE INDEX IF NOT EXISTS idx_products_category ON products (category);

-- Index on active flag to speed up findByActive queries (storefront listing).
CREATE INDEX IF NOT EXISTS idx_products_active ON products (active);

-- Composite index for category + active — covers the findByCategoryAndActive query.
CREATE INDEX IF NOT EXISTS idx_products_category_active ON products (category, active);

-- Index on price for price-range queries (ORDER BY price is also covered).
CREATE INDEX IF NOT EXISTS idx_products_price ON products (price);

-- ── Seed data ────────────────────────────────────────────────────────────────────
-- A small set of sample products to make the application immediately useful
-- after first boot (no need to POST data manually before trying GET endpoints).

INSERT INTO products (name, description, price, category, stock_quantity, active) VALUES
    ('Wireless Mechanical Keyboard',  'Compact TKL layout with Cherry MX switches, USB-C charging, and 80h battery life', 129.99, 'electronics',  45, TRUE),
    ('Ergonomic Office Mouse',        'Vertical design reduces wrist strain; 6 programmable buttons; silent click', 59.99,  'electronics',  120, TRUE),
    ('USB-C Hub 7-in-1',              'Expands a single USB-C port into HDMI 4K, 3× USB-A, SD, microSD, and 100W PD', 49.99,  'electronics',  3,  TRUE),
    ('Standing Desk Converter',       'Adjustable height 15–45 cm; fits monitors up to 27 inches; no assembly required', 199.99, 'furniture',    18, TRUE),
    ('Noise-Cancelling Headphones',   'Over-ear ANC; 30h playback; foldable; includes travel case', 249.99, 'electronics',  0,  FALSE),
    ('LED Desk Lamp',                 '5 colour temperatures; touch dimmer; USB-A charging port on the base', 39.99,  'furniture',    5,  TRUE),
    ('Mechanical Pencil Set',         'Pack of 12 precision 0.5 mm mechanical pencils with HB lead refills', 12.99,  'stationery',   200, TRUE),
    ('Hardcover Notebook A5',         '200 pages dot-grid paper; lay-flat binding; elastic closure band', 18.99,  'stationery',   75, TRUE),
    ('Laptop Stand Aluminium',        'Adjustable angle 15–50°; compatible with laptops 10–17 inches; foldable', 34.99,  'electronics',  2,  TRUE),
    ('Cable Management Kit',          '100-piece kit: clips, velcro ties, cable sleeves, and adhesive mounts', 15.99,  'accessories',  60, TRUE);
