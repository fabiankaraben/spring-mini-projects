package com.example.debeziumcdc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs once at application startup (after the Spring context is fully initialized)
 * to configure PostgreSQL-specific settings that Hibernate cannot set via JPA.
 *
 * <h2>Why REPLICA IDENTITY FULL?</h2>
 * <p>PostgreSQL's default {@code REPLICA IDENTITY} mode is {@code DEFAULT}, which
 * means:
 * <ul>
 *   <li>For UPDATE events: the {@code before} image contains only the primary key.</li>
 *   <li>For DELETE events: the {@code before} image contains only the primary key;
 *       all other columns are {@code null}.</li>
 * </ul>
 *
 * <p>Setting {@code REPLICA IDENTITY FULL} on the {@code products} table causes
 * PostgreSQL to include ALL column values in the before image of every WAL record.
 * This allows Debezium to populate the {@code before} snapshot in
 * {@link com.example.debeziumcdc.cdc.ProductCdcEvent} with the full deleted/updated
 * row data — not just the ID.
 *
 * <p>This is safe to run on every startup:
 * {@code ALTER TABLE ... REPLICA IDENTITY FULL} is idempotent.
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes post-startup SQL DDL statements.
     *
     * <p>This runs after Hibernate's {@code ddl-auto: update} has created or
     * updated the {@code products} table, so the table is guaranteed to exist
     * before this statement runs.
     *
     * @param args application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("Setting REPLICA IDENTITY FULL on products table for full CDC before-images...");
        jdbcTemplate.execute("ALTER TABLE products REPLICA IDENTITY FULL");
        log.info("REPLICA IDENTITY FULL set on products table.");
    }
}
