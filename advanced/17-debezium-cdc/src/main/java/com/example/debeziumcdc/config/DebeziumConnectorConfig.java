package com.example.debeziumcdc.config;

import com.example.debeziumcdc.cdc.CdcEventDispatcher;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configures and starts the Debezium Embedded Engine for PostgreSQL CDC.
 *
 * <p>The Debezium Embedded Engine runs inside the Spring Boot process.
 * It connects to PostgreSQL via JDBC and also through the logical replication
 * protocol to read WAL records. The engine is started on a background thread
 * ({@link Executor}) and calls back into {@link CdcEventDispatcher} with each
 * batch of {@link ChangeEvent} records.
 *
 * <p>Key connector properties explained:
 * <ul>
 *   <li>{@code connector.class} — the Debezium connector implementation to use.</li>
 *   <li>{@code plugin.name=pgoutput} — PostgreSQL's built-in logical decoding plugin
 *       (available since PG 10). No extension install needed unlike decoderbufs.</li>
 *   <li>{@code slot.name} — name of the PostgreSQL replication slot. The slot holds
 *       WAL positions so Debezium can resume after restarts without re-reading old WAL.</li>
 *   <li>{@code publication.name} — PostgreSQL publication that defines which tables
 *       to replicate. Debezium auto-creates it when {@code publication.autocreate.mode}
 *       is set to {@code all_tables}.</li>
 *   <li>{@code table.include.list} — limits CDC to specific tables (reduces noise).</li>
 *   <li>{@code offset.storage} — where to store the current WAL offset.
 *       In-memory (MemoryOffsetBackingStore) is fine for demos; in production use
 *       FileOffsetBackingStore or KafkaOffsetBackingStore.</li>
 *   <li>{@code snapshot.mode=initial} — on first start, take a full snapshot of the
 *       existing table rows before switching to streaming mode.</li>
 * </ul>
 */
@Configuration
public class DebeziumConnectorConfig {

    private static final Logger log = LoggerFactory.getLogger(DebeziumConnectorConfig.class);

    // -------------------------------------------------------------------------
    // Injected properties (from application.yml)
    // -------------------------------------------------------------------------

    @Value("${debezium.connector.database.hostname}")
    private String dbHostname;

    @Value("${debezium.connector.database.port}")
    private String dbPort;

    @Value("${debezium.connector.database.user}")
    private String dbUser;

    @Value("${debezium.connector.database.password}")
    private String dbPassword;

    @Value("${debezium.connector.database.dbname}")
    private String dbName;

    @Value("${debezium.connector.database.server.id:1}")
    private String serverId;

    // -------------------------------------------------------------------------
    // Beans
    // -------------------------------------------------------------------------

    /**
     * Builds the {@link Properties} object that configures the Debezium PostgreSQL connector.
     *
     * <p>All properties are documented inline. Full reference:
     * https://debezium.io/documentation/reference/stable/connectors/postgresql.html
     *
     * @return connector configuration properties
     */
    @Bean
    public Properties debeziumConnectorProperties() {
        Properties props = new Properties();

        // --- Connector class ---
        // Tells Debezium which connector implementation to load.
        props.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");

        // --- Connector name (unique identifier for this connector instance) ---
        props.setProperty("name", "product-postgres-connector");

        // --- Database connection ---
        props.setProperty("database.hostname", dbHostname);
        props.setProperty("database.port", dbPort);
        props.setProperty("database.user", dbUser);
        props.setProperty("database.password", dbPassword);
        props.setProperty("database.dbname", dbName);

        // --- Logical decoding plugin ---
        // pgoutput is built into PostgreSQL 10+ and requires no extra extensions.
        // It uses the PUBLICATION/SUBSCRIPTION replication mechanism.
        props.setProperty("plugin.name", "pgoutput");

        // --- Replication slot ---
        // Debezium creates this slot on the PostgreSQL server. The slot tracks
        // which WAL position has been consumed so CDC can resume after restarts.
        // IMPORTANT: The slot must be unique per connector instance. If you run
        // multiple connectors against the same DB, use different slot names.
        props.setProperty("slot.name", "debezium_product_slot");

        // --- Publication ---
        // A PostgreSQL PUBLICATION defines which tables to include in replication.
        // Debezium will auto-create a publication named "debezium_product_pub"
        // covering all tables when publication.autocreate.mode=all_tables.
        props.setProperty("publication.name", "debezium_product_pub");
        props.setProperty("publication.autocreate.mode", "all_tables");

        // --- Topic prefix (replaces deprecated database.server.name) ---
        // Debezium uses this prefix for topic names in Kafka Connect mode.
        // In embedded mode it's used as part of the engine's logical name.
        props.setProperty("topic.prefix", "dbserver1");

        // --- Table filter ---
        // Only capture changes from the products table in the public schema.
        // Format: <schema>.<table>
        props.setProperty("table.include.list", "public.products");

        // --- Offset storage ---
        // MemoryOffsetBackingStore stores WAL offsets in-memory (lost on restart).
        // For production, use FileOffsetBackingStore or KafkaOffsetBackingStore.
        props.setProperty("offset.storage",
                "org.apache.kafka.connect.storage.MemoryOffsetBackingStore");

        // --- Offset flush interval ---
        // How often (ms) to flush the current WAL offset to the offset store.
        props.setProperty("offset.flush.interval.ms", "1000");

        // --- Snapshot mode ---
        // "initial": on first start, snapshot existing rows then switch to streaming.
        // "never": skip snapshot (only capture changes after the connector starts).
        // "always": re-snapshot on every start (useful for testing).
        props.setProperty("snapshot.mode", "initial");

        // --- JSON serialization (no schemas) ---
        // Disabling schemas keeps the JSON messages compact and avoids needing a
        // Schema Registry. The dispatcher parses the raw JSON envelope directly.
        props.setProperty("key.converter",
                "org.apache.kafka.connect.json.JsonConverter");
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter",
                "org.apache.kafka.connect.json.JsonConverter");
        props.setProperty("value.converter.schemas.enable", "false");

        // --- Replica identity ---
        // PostgreSQL defaults to REPLICA IDENTITY DEFAULT, which only includes
        // the primary key column in DELETE events (the before image has nulls for
        // all other columns). Setting FULL causes PostgreSQL to include ALL column
        // values in the before image of DELETE (and UPDATE) events.
        // Debezium can set this automatically when it first connects to a table.
        props.setProperty("replica.identity.autoset.values", "public.products:FULL");

        // --- Decimal handling mode ---
        // By default, Debezium encodes PostgreSQL NUMERIC columns as base64 binary
        // (Kafka Connect "BYTES" logical type). Setting "string" causes Debezium to
        // emit them as plain decimal strings (e.g. "1299.99"), which the
        // CdcEventDispatcher parses directly with new BigDecimal(rawPrice.toString()).
        props.setProperty("decimal.handling.mode", "string");

        // --- Schema history (in-memory, for embedded use) ---
        // Debezium requires a schema history store to track DDL changes.
        // MemorySchemaHistory is fine for demos; use FileSchemaHistory or
        // KafkaSchemaHistory in production.
        props.setProperty("schema.history.internal",
                "io.debezium.storage.memory.MemorySchemaHistory");

        // --- Error handling ---
        // On connector error, log and continue (do not stop the engine).
        props.setProperty("errors.tolerance", "all");
        props.setProperty("errors.log.enable", "true");

        log.info("Debezium connector configured: host={}:{}, db={}", dbHostname, dbPort, dbName);
        return props;
    }

    /**
     * Creates and configures the Debezium Embedded Engine.
     *
     * <p>The engine:
     * <ol>
     *   <li>Uses {@link Json} format so change events are delivered as JSON strings.</li>
     *   <li>Reads connector properties from {@link #debeziumConnectorProperties()}.</li>
     *   <li>Delegates event processing to {@link CdcEventDispatcher}.</li>
     *   <li>Logs notifications on completion or error.</li>
     * </ol>
     *
     * <p>The engine is NOT auto-started here — it is started by
     * {@link DebeziumEngineRunner#startEngine()} after the Spring context is fully
     * initialized.
     *
     * @param properties         the connector configuration
     * @param cdcEventDispatcher the event handler
     * @return the configured (but not yet started) {@link DebeziumEngine}
     */
    @Bean
    public DebeziumEngine<ChangeEvent<String, String>> debeziumEngine(
            @Qualifier("debeziumConnectorProperties") Properties properties,
            CdcEventDispatcher cdcEventDispatcher) {

        return DebeziumEngine.create(Json.class)
                .using(properties)
                .notifying(cdcEventDispatcher)
                .using((success, message, error) -> {
                    if (!success) {
                        log.error("Debezium engine completed with failure: {}", message, error);
                    } else {
                        log.info("Debezium engine completed successfully: {}", message);
                    }
                })
                .build();
    }

    /**
     * Creates a single-thread executor to run the Debezium engine.
     *
     * <p>The Debezium Embedded Engine is a blocking loop that continuously
     * reads from the WAL. It must run on a dedicated thread separate from
     * the main Spring application thread.
     *
     * @return a single-thread executor for the Debezium engine
     */
    @Bean
    public Executor debeziumEngineExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "debezium-engine-thread");
            t.setDaemon(true); // Do not prevent JVM shutdown
            return t;
        });
    }
}
