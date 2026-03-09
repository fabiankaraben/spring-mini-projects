package com.example.kafkaconsumer.listener;

import com.example.kafkaconsumer.domain.OrderEvent;
import com.example.kafkaconsumer.service.OrderEventProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that consumes {@link OrderEvent} messages from the configured topic.
 *
 * <h2>How {@code @KafkaListener} works</h2>
 * <p>Spring Kafka's {@code @KafkaListener} annotation marks a method as the handler
 * for messages arriving on one or more Kafka topics. At startup, Spring Kafka:
 * <ol>
 *   <li>Creates a {@code KafkaMessageListenerContainer} (or
 *       {@code ConcurrentMessageListenerContainer} if {@code concurrency > 1}).</li>
 *   <li>Starts one or more poll loops, each on its own thread, that call
 *       {@code KafkaConsumer.poll()} in a tight loop.</li>
 *   <li>For each {@code ConsumerRecord} returned by a poll, deserialises the
 *       key and value using the configured deserialisers, then invokes this method.</li>
 * </ol>
 *
 * <h2>Manual acknowledgement (MANUAL_IMMEDIATE mode)</h2>
 * <p>With {@code ackMode = MANUAL_IMMEDIATE}, Spring Kafka does NOT commit the
 * consumer offset automatically after the listener method returns. Instead the
 * listener must call {@link Acknowledgment#acknowledge()} explicitly to commit
 * the offset. This gives us full control over when the offset is advanced:
 * <ul>
 *   <li>We acknowledge <em>after</em> the service has successfully processed the
 *       event, so a crash before processing does not silently skip the message.</li>
 *   <li>If processing throws an exception the offset is not committed and the
 *       message will be redelivered on the next poll.</li>
 * </ul>
 *
 * <h2>Using {@code ConsumerRecord} as the parameter type</h2>
 * <p>Accepting a {@link ConsumerRecord}{@code <String, OrderEvent>} instead of
 * a plain {@code OrderEvent} lets us access the Kafka message metadata (topic,
 * partition, offset, headers, timestamp) alongside the deserialised value.
 * This metadata is passed to the service layer so it can be stored and exposed
 * via the REST API for observability.
 *
 * <h2>Separation of concerns</h2>
 * <p>This class is intentionally thin: it handles only Kafka concerns (reading
 * messages, committing offsets, error logging) and delegates all business logic
 * to {@link OrderEventProcessorService}. This makes the service independently
 * unit-testable without any Kafka infrastructure.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    /**
     * The service that contains the actual processing logic.
     * Injected via constructor for testability.
     * Typed as the {@link OrderEventProcessor} interface so Mockito can create a
     * proxy in unit tests without bytecode manipulation of a concrete class.
     */
    private final OrderEventProcessor processorService;

    /**
     * Constructor injection – Spring will provide the {@link com.example.kafkaconsumer.service.OrderEventProcessorService}
     * bean automatically (it implements {@link OrderEventProcessor}).
     *
     * @param processorService the service that processes consumed events
     */
    public OrderEventListener(OrderEventProcessor processorService) {
        this.processorService = processorService;
    }

    /**
     * Consumes an order event from the Kafka topic and forwards it for processing.
     *
     * <p>The method signature parameters are resolved by Spring Kafka:
     * <ul>
     *   <li>{@code record} – the full Kafka record including metadata and the
     *       deserialised {@link OrderEvent} value.</li>
     *   <li>{@code ack} – the acknowledgement handle; calling
     *       {@link Acknowledgment#acknowledge()} commits the consumer offset
     *       to Kafka so the message is not redelivered.</li>
     * </ul>
     *
     * <p>Processing flow:
     * <ol>
     *   <li>Log the incoming record for traceability.</li>
     *   <li>Delegate to {@link OrderEventProcessorService#process} which applies
     *       domain logic and stores the processed event.</li>
     *   <li>Acknowledge the offset only on success, so a processing failure
     *       causes the message to be redelivered (at-least-once semantics).</li>
     * </ol>
     *
     * @param record the Kafka consumer record containing the order event and metadata
     * @param ack    the acknowledgement handle for committing the consumer offset
     */
    @KafkaListener(
            topics = "${app.kafka.topic.orders}",
            groupId = "${spring.kafka.consumer.group-id}",
            // containerFactory references a KafkaListenerContainerFactory bean.
            // "kafkaListenerContainerFactory" is the default name auto-configured
            // by Spring Boot based on the consumer properties in application.yml.
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
        log.debug("Received Kafka record [topic={}, partition={}, offset={}, key={}]",
                record.topic(), record.partition(), record.offset(), record.key());

        try {
            // Delegate processing to the service layer – this is where
            // business logic lives (status-specific handlers, storage, etc.)
            processorService.process(record.value(), record.partition(), record.offset());

            // Commit the offset only after successful processing.
            // This implements at-least-once delivery: if process() throws,
            // the offset is not committed and the message will be redelivered.
            ack.acknowledge();

        } catch (Exception e) {
            // Log the error but do NOT acknowledge, so the message is retried.
            // In production you would also consider a dead-letter topic for
            // messages that fail repeatedly to avoid consumer lag buildup.
            log.error("Failed to process order event [topic={}, partition={}, offset={}, key={}]: {}",
                    record.topic(), record.partition(), record.offset(), record.key(), e.getMessage(), e);
            // Re-throw so the container's error handler can manage retry/DLT logic
            throw new RuntimeException("Failed to process order event at offset " + record.offset(), e);
        }
    }
}
