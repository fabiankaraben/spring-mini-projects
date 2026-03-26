package com.example.debeziumcdc.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Demo Kafka consumer that listens on the {@code product-cdc-events} topic.
 *
 * <p>In a real system, this would be a separate downstream microservice
 * (e.g. a search indexer, a cache invalidator, or an audit logger). Here,
 * it lives in the same application to demonstrate the full round-trip:
 *
 * <pre>
 *   REST API → PostgreSQL WAL → Debezium Embedded Engine → Kafka → CdcEventConsumer
 * </pre>
 *
 * <p>The consumer simply logs each received event with its operation type and
 * the relevant product state, making the CDC pipeline visible in the application logs.
 *
 * <p>Spring Kafka auto-configures the consumer group ID and deserializers
 * from {@code application.yml}. The {@code @KafkaListener} annotation binds
 * this method to the topic defined in {@link CdcEventDispatcher#CDC_TOPIC}.
 */
@Component
public class CdcEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CdcEventConsumer.class);

    /**
     * Handles a {@link ProductCdcEvent} consumed from the {@code product-cdc-events} topic.
     *
     * <p>Switch on the operation type to apply different logic:
     * <ul>
     *   <li>CREATE / READ — a new product has appeared; downstream can index it.</li>
     *   <li>UPDATE — a product was modified; downstream should refresh cached views.</li>
     *   <li>DELETE — a product was removed; downstream should clean up derived data.</li>
     * </ul>
     *
     * @param event the deserialized CDC event from Kafka
     */
    @KafkaListener(topics = CdcEventDispatcher.CDC_TOPIC, groupId = "cdc-event-consumer-group")
    public void onCdcEvent(ProductCdcEvent event) {
        switch (event.getOperation()) {
            case CREATE, READ -> log.info(
                    "[CDC Consumer] PRODUCT CREATED — id={}, name='{}', price={}, stock={}",
                    event.getAfter().getId(),
                    event.getAfter().getName(),
                    event.getAfter().getPrice(),
                    event.getAfter().getStock());

            case UPDATE -> log.info(
                    "[CDC Consumer] PRODUCT UPDATED — id={}, name='{}' → '{}', price={} → {}, stock={} → {}",
                    event.getAfter().getId(),
                    event.getBefore() != null ? event.getBefore().getName() : "?",
                    event.getAfter().getName(),
                    event.getBefore() != null ? event.getBefore().getPrice() : "?",
                    event.getAfter().getPrice(),
                    event.getBefore() != null ? event.getBefore().getStock() : "?",
                    event.getAfter().getStock());

            case DELETE -> log.info(
                    "[CDC Consumer] PRODUCT DELETED — id={}, name='{}'",
                    event.getBefore() != null ? event.getBefore().getId() : "?",
                    event.getBefore() != null ? event.getBefore().getName() : "?");

            default -> log.warn("[CDC Consumer] Unknown operation: {}", event.getOperation());
        }
    }
}
