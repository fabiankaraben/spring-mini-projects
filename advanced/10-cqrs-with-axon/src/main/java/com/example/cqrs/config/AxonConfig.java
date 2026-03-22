package com.example.cqrs.config;

import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Axon Framework configuration for the embedded event store backed by JPA/PostgreSQL.
 *
 * <h2>Why we need this class</h2>
 * By default, Axon tries to connect to an Axon Server instance. Since we excluded the
 * {@code axon-server-connector} in the POM (we want a self-contained app with no
 * external Axon Server dependency), we must manually configure an embedded event store
 * that uses the JPA {@code EventStorageEngine}.
 *
 * <h2>What each bean does</h2>
 * <ul>
 *   <li>{@link EventStorageEngine} — low-level storage adapter; {@link JpaEventStorageEngine}
 *       persists events as rows in the {@code domain_event_entry} and
 *       {@code snapshot_event_entry} tables (auto-created by Hibernate DDL).</li>
 *   <li>{@link EmbeddedEventStore} — wraps the engine and provides the in-process
 *       event bus; events published via {@code AggregateLifecycle.apply()} flow through
 *       here to reach {@code @EventHandler} methods in the projection.</li>
 * </ul>
 *
 * <h2>Tables created automatically</h2>
 * Axon's JPA entities (shipped in {@code axon-eventsourcing}) are discovered by
 * Hibernate when {@code spring.jpa.hibernate.ddl-auto=update} is set.
 * The tables are:
 * <pre>
 *   domain_event_entry    — one row per event per aggregate
 *   snapshot_event_entry  — periodic snapshots to speed up aggregate loading
 *   saga_entry            — (not used in this project, but created anyway)
 *   association_value_entry — (same)
 * </pre>
 */
@Configuration
public class AxonConfig {

    /**
     * Configures all event processors to use SUBSCRIBING mode.
     *
     * <p>In SUBSCRIBING mode the event handler is invoked synchronously on the same
     * thread that published the event (inside the command handler's Unit of Work).
     * This guarantees that the read model is fully updated before
     * {@code commandGateway.sendAndWait()} returns, eliminating any
     * eventual-consistency gap between the write and read sides in this
     * single-process deployment.
     *
     * @param configurer the Axon event-processing configurer (auto-configured by Axon Spring Boot)
     */
    @Autowired
    public void configureEventProcessing(EventProcessingConfigurer configurer) {
        configurer.usingSubscribingEventProcessors();
    }

    /**
     * Configures the JPA-backed event storage engine.
     *
     * <p>{@link JpaEventStorageEngine} stores events in the {@code domain_event_entry} table.
     * It uses the Spring-managed {@link EntityManagerProvider} (provided by Axon's Spring Boot
     * auto-configuration) to participate in Spring's transaction management.
     *
     * @param entityManagerProvider Axon's JPA entity manager provider (auto-configured)
     * @param transactionManager    Axon's Spring transaction manager wrapper (auto-configured)
     * @param serializer            the default Axon serializer (XStream or Jackson, auto-configured)
     * @return the configured storage engine
     */
    @Bean
    public EventStorageEngine eventStorageEngine(
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager,
            Serializer serializer) {

        return JpaEventStorageEngine.builder()
                .entityManagerProvider(entityManagerProvider)
                .transactionManager(transactionManager)
                .eventSerializer(serializer)
                .snapshotSerializer(serializer)
                .build();
    }

    /**
     * Configures the embedded (in-process) event store that wraps the storage engine.
     *
     * <p>The {@link EmbeddedEventStore} delivers events to local {@code @EventHandler}
     * methods synchronously within the same JVM process. This is ideal for a monolith or
     * a simple microservice where all components live in the same application context.
     *
     * <p>For multi-node deployments, replace this with an Axon Server connector
     * or a message broker extension (Kafka, RabbitMQ).
     *
     * <p>{@link NoOpMessageMonitor} is used here because we do not need metrics monitoring
     * in this educational project. In production you would wire in a micrometer-based
     * monitor via the AxonConfiguration message monitor factory.
     *
     * @param storageEngine the JPA storage engine defined above
     * @return the configured embedded event store
     */
    @Bean
    public EmbeddedEventStore eventStore(EventStorageEngine storageEngine) {
        return EmbeddedEventStore.builder()
                .storageEngine(storageEngine)
                .messageMonitor(NoOpMessageMonitor.instance())
                .build();
    }
}
