package com.example.debeziumcdc.config;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * Manages the lifecycle of the Debezium Embedded Engine.
 *
 * <p>The engine is started after the entire Spring application context is
 * refreshed (i.e., all beans are initialized), ensuring that the
 * {@link com.example.debeziumcdc.cdc.CdcEventDispatcher} and
 * {@link org.springframework.kafka.core.KafkaTemplate} are ready before
 * any CDC events arrive.
 *
 * <p>The engine is stopped gracefully when the Spring context is closed
 * (e.g., when the application receives a SIGTERM signal).
 *
 * <p>Why use {@code @EventListener(ContextRefreshedEvent.class)} instead of
 * {@code @PostConstruct}?
 * <ul>
 *   <li>{@code @PostConstruct} runs during the bean initialization phase, before
 *       all other beans (including KafkaTemplate) have been fully wired. Starting
 *       the Debezium engine at that point could cause NullPointerExceptions.</li>
 *   <li>{@code ContextRefreshedEvent} fires after the full context is ready, making
 *       it safe to start background threads that call other beans.</li>
 * </ul>
 */
@Component
public class DebeziumEngineRunner {

    private static final Logger log = LoggerFactory.getLogger(DebeziumEngineRunner.class);

    private final DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;
    private final Executor debeziumEngineExecutor;

    /** Flag to prevent the engine from being started multiple times on context refreshes. */
    private volatile boolean started = false;

    public DebeziumEngineRunner(DebeziumEngine<ChangeEvent<String, String>> debeziumEngine,
                                Executor debeziumEngineExecutor) {
        this.debeziumEngine = debeziumEngine;
        this.debeziumEngineExecutor = debeziumEngineExecutor;
    }

    /**
     * Starts the Debezium engine on the background executor thread once the
     * Spring application context has been fully refreshed.
     *
     * <p>The engine begins connecting to PostgreSQL, creating the replication slot,
     * taking an initial snapshot (if configured), and then entering streaming mode
     * to consume WAL records in real-time.
     *
     * <p>The {@code synchronized} block ensures that even if
     * {@code ContextRefreshedEvent} is fired multiple times (e.g., child contexts),
     * the engine starts only once.
     *
     * @param event the Spring context refresh event (unused but required by the annotation)
     */
    @EventListener(ContextRefreshedEvent.class)
    public synchronized void startEngine(ContextRefreshedEvent event) {
        if (started) {
            return; // Prevent double-start on child context refreshes
        }
        started = true;
        log.info("Starting Debezium Embedded Engine on background thread...");
        debeziumEngineExecutor.execute(debeziumEngine);
    }

    /**
     * Stops the Debezium engine gracefully when the Spring context is closed.
     *
     * <p>Calling {@link DebeziumEngine#close()} signals the engine to:
     * <ol>
     *   <li>Stop reading from the WAL.</li>
     *   <li>Flush the current offset to the offset store.</li>
     *   <li>Release the replication slot connection.</li>
     *   <li>Exit the background thread.</li>
     * </ol>
     *
     * <p>This ensures the PostgreSQL replication slot is not left in an "active"
     * state, which would prevent WAL segment reclamation on the server.
     */
    @PreDestroy
    public void stopEngine() {
        log.info("Stopping Debezium Embedded Engine...");
        try {
            debeziumEngine.close();
            log.info("Debezium Embedded Engine stopped successfully.");
        } catch (IOException e) {
            log.error("Error while stopping Debezium engine: {}", e.getMessage(), e);
        }
    }
}
