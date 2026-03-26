-- ============================================================================
-- V1__create_orders_table.sql
--
-- Flyway migration script: creates the orders table.
--
-- Flyway naming convention: V{version}__{description}.sql
--   - V1       : version number (Flyway runs migrations in order)
--   - __       : double underscore separator (required)
--   - create_orders_table : human-readable description
--
-- This script runs once at application startup (before Hibernate validates
-- the schema). Flyway records executed scripts in the flyway_schema_history
-- table to ensure idempotency (scripts are never run twice).
-- ============================================================================

-- The orders table stores e-commerce orders managed by the Camunda fulfilment process.
--
-- Column notes:
--   id                 : BIGSERIAL is PostgreSQL's auto-incrementing BIGINT.
--                        JPA maps this to Long with GenerationType.IDENTITY.
--   status             : VARCHAR(30) stores the OrderStatus enum as a string.
--                        VARCHAR is more readable than storing ordinal integers.
--   process_instance_id: The Camunda process instance ID linking this order to
--                        its workflow state in Camunda's runtime tables.
--   tracking_number    : Assigned by the ShippingDelegate; NULL until shipped.
--   total_amount       : Computed by PaymentProcessingDelegate (qty × unit_price).
--   error_message      : Populated only when status = 'FAILED'.
--   created_at         : Set once at insert time; never updated.
--   updated_at         : Updated by each delegate as the order progresses.
CREATE TABLE IF NOT EXISTS orders (
    id                  BIGSERIAL       PRIMARY KEY,
    customer_name       VARCHAR(255)    NOT NULL,
    product_name        VARCHAR(255)    NOT NULL,
    quantity            INTEGER         NOT NULL CHECK (quantity > 0),
    unit_price          NUMERIC(10, 2)  NOT NULL CHECK (unit_price > 0),
    total_amount        NUMERIC(10, 2),
    status              VARCHAR(30)     NOT NULL,
    process_instance_id VARCHAR(64),
    tracking_number     VARCHAR(100),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    error_message       VARCHAR(500)
);

-- Index on status for efficient filtering by order status
-- (GET /api/orders?status=COMPLETED queries this column).
CREATE INDEX IF NOT EXISTS idx_orders_status
    ON orders (status);

-- Index on process_instance_id for efficient lookup by Camunda process instance
-- (OrderRepository.findByProcessInstanceId queries this column).
CREATE INDEX IF NOT EXISTS idx_orders_process_instance_id
    ON orders (process_instance_id)
    WHERE process_instance_id IS NOT NULL;

-- Index on customer_name for efficient lookup by customer.
CREATE INDEX IF NOT EXISTS idx_orders_customer_name
    ON orders (customer_name);
