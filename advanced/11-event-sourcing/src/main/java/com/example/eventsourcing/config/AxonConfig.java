package com.example.eventsourcing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Axon Framework configuration for the embedded JPA-backed event store.
 *
 * <h2>Why this class exists</h2>
 * By default, Axon's Spring Boot starter tries to connect to an <em>Axon Server</em>
 * instance for event storage and routing. We excluded the {@code axon-server-connector}
 * in the POM to keep this project self-contained (no external Axon Server required).
 * Without it, we must manually wire an embedded event store backed by JPA.
 *
 * <h2>Event store architecture</h2>
 * <pre>
 *   AggregateLifecycle.apply(event)
 *     → EmbeddedEventStore (in-process event bus)
 *       → JpaEventStorageEngine (persists to domain_event_entry table)
 *         → PostgreSQL (via Spring-managed JPA EntityManager)
 *     → EventHandler methods (projection updates, same transaction)
 * </pre>
 *
 * <h2>Tables auto-created by Hibernate DDL</h2>
 * Axon provides JPA entity classes (in the {@code axon-eventsourcing} jar) that Hibernate
 * picks up and creates as tables on startup when {@code spring.jpa.hibernate.ddl-auto=update}:
 * <pre>
 *   domain_event_entry      — one row per domain event per aggregate (the event log)
 *   snapshot_event_entry    — periodic state snapshots to speed up aggregate loading
 *   saga_entry              — Axon Saga state (not used in this project)
 *   association_value_entry — Axon Saga association values (not used here)
 * </pre>
 *
 * <h2>Subscribing vs. Tracking event processors</h2>
 * <ul>
 *   <li><strong>Subscribing</strong> (used here): event handlers run synchronously on
 *       the command thread, in the same Unit of Work / transaction. The read model is
 *       immediately consistent after each command completes.</li>
 *   <li><strong>Tracking</strong> (default in multi-node): event handlers run asynchronously
 *       in a background thread pool, reading from the event store using a token-based
 *       tracking mechanism. Provides eventual consistency and is suitable for production.</li>
 * </ul>
 */
@Configuration
public class AxonConfig {

    /**
     * Configures all event processors to use SUBSCRIBING mode.
     *
     * <p>In SUBSCRIBING mode the event handler ({@code AccountProjection}) is called
     * synchronously on the same thread as the command handler, within the same Unit of Work.
     * This ensures the read model is up-to-date before {@code sendAndWait()} returns.
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
     * <p>{@link JpaEventStorageEngine} appends events to the {@code domain_event_entry} table.
     * Each row contains:
     * <ul>
     *   <li>aggregate identifier and type</li>
     *   <li>sequence number (monotonically increasing per aggregate)</li>
     *   <li>event type (fully-qualified class name)</li>
     *   <li>serialized event payload (JSON or XStream XML)</li>
     *   <li>event metadata</li>
     *   <li>global index (used for tracking processors)</li>
     * </ul>
     *
     * @param entityManagerProvider Axon's JPA entity manager provider (auto-configured)
     * @param transactionManager    Axon's Spring transaction manager wrapper (auto-configured)
     * @param serializer            the default Axon serializer (XStream or Jackson, auto-configured)
     * @return the configured storage engine
     */

    /**
     * Configures Jackson as the Axon serializer instead of the default XStream.
     *
     * <h2>Why Jackson instead of XStream?</h2>
     * The event and command API classes in this project are Java <em>records</em>.
     * XStream's {@code ReflectionConverter} uses {@code sun.misc.Unsafe.objectFieldOffset()}
     * to access fields, which throws {@code UnsupportedOperationException} on Java records
     * because records are immutable value types and do not expose mutable field offsets.
     *
     * <p>Jackson handles records natively (since Jackson 2.12) via its
     * {@code RecordNamingStrategyPatchModule} and constructor-based deserialization.
     *
     * <p>The {@code @Primary} annotation ensures this bean is used wherever Axon
     * auto-wires a {@link Serializer} — including the event store, command bus,
     * and message serializers.
     *
     * @return a Jackson-backed Axon serializer with Java Time support
     */
    @Bean
    @Primary
    public Serializer axonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return JacksonSerializer.builder()
                .objectMapper(objectMapper)
                .build();
    }

    /**
     * Configures the JPA-backed event storage engine.
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
     * Configures the embedded (in-process) event store.
     *
     * <p>The {@link EmbeddedEventStore} wraps the {@link EventStorageEngine} and provides
     * the in-process event bus. Events applied via {@code AggregateLifecycle.apply()} flow
     * through this store to reach {@code @EventHandler} methods in the projection.
     *
     * <p>For multi-node deployments, replace this with an Axon Server connector or a
     * message broker extension (Kafka, RabbitMQ, AMQP).
     *
     * @param storageEngine the JPA storage engine configured above
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
