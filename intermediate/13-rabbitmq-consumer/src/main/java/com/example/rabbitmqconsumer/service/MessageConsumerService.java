package com.example.rabbitmqconsumer.service;

import com.example.rabbitmqconsumer.domain.OrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service layer responsible for consuming order messages from the RabbitMQ queue.
 *
 * <p>This class is the core of the consumer application. It demonstrates how
 * Spring AMQP's {@code @RabbitListener} annotation wires a plain Java method
 * to a RabbitMQ queue so that every message published to that queue automatically
 * triggers the annotated method.
 *
 * <h2>How @RabbitListener works</h2>
 * <ol>
 *   <li>Spring AMQP creates a {@link org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer}
 *       bean at startup for each {@code @RabbitListener} annotation.</li>
 *   <li>The container opens a persistent AMQP channel to RabbitMQ and issues a
 *       {@code basic.consume} command on the specified queue.</li>
 *   <li>When a message arrives, the container pulls it off the channel, converts
 *       the raw AMQP message body from JSON to the method parameter type using
 *       the {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}
 *       configured in {@link com.example.rabbitmqconsumer.config.RabbitMQConfig},
 *       and invokes the listener method.</li>
 *   <li>If the method completes normally, the container sends a RabbitMQ
 *       {@code basic.ack} acknowledging the message so it is removed from the queue.</li>
 *   <li>If the method throws an exception, the container sends a {@code basic.nack}
 *       and, because the main queue has a dead-letter exchange configured, RabbitMQ
 *       routes the message to the dead-letter queue (DLQ) instead of re-queuing it
 *       indefinitely.</li>
 * </ol>
 *
 * <h2>State tracking for observability</h2>
 * <p>This service maintains an in-memory list of processed messages and a counter.
 * In a real production application, processed orders would be persisted to a
 * database, forwarded to another service, or written to an audit log. Here the
 * in-memory state lets us verify consumption via the REST API at
 * {@code GET /api/messages} and in integration tests.
 *
 * <h2>Thread safety</h2>
 * <p>{@link AtomicLong} is used for the counter because the listener container
 * may invoke the listener on multiple threads concurrently (configurable via
 * {@code spring.rabbitmq.listener.simple.concurrency}). The
 * {@link Collections#synchronizedList} wrapper ensures the message log is also
 * safe to read while the listener is writing.
 */
@Service
public class MessageConsumerService {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumerService.class);

    /**
     * Thread-safe counter incremented on every successfully processed message.
     * Uses {@link AtomicLong} to avoid race conditions when the listener container
     * runs multiple consumer threads concurrently.
     */
    private final AtomicLong messageCount = new AtomicLong(0);

    /**
     * In-memory log of the last N processed messages, kept for demonstration and testing.
     *
     * <p>In production this would be persisted to a database. Here we cap the
     * list at {@link #MAX_STORED_MESSAGES} to avoid unbounded memory growth.
     */
    private final List<ProcessedMessage> processedMessages =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * Maximum number of messages to keep in the in-memory log.
     * Older entries are discarded when this limit is reached.
     */
    private static final int MAX_STORED_MESSAGES = 100;

    // ── @RabbitListener ────────────────────────────────────────────────────────────

    /**
     * Consume an order message from the {@code orders.queue}.
     *
     * <p>Spring AMQP automatically:
     * <ul>
     *   <li>Deserialises the JSON message body into an {@link OrderMessage} using
     *       the {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter}.</li>
     *   <li>ACKs the message after this method returns normally.</li>
     *   <li>Routes the message to the DLQ if this method throws an exception.</li>
     * </ul>
     *
     * <p>The {@code ${spring.rabbitmq.queue}} placeholder is resolved from
     * {@code application.yml} at startup, keeping the queue name configuration
     * in one place.
     *
     * @param message the order message deserialized from the RabbitMQ queue
     */
    @RabbitListener(queues = "${spring.rabbitmq.queue}")
    public void consumeOrder(OrderMessage message) {
        log.info("Received order message: {}", message);

        // Validate that the message has the required fields before processing.
        // In production this would trigger business logic (e.g. update inventory,
        // send a confirmation email, trigger a shipment).
        if (message.getOrderId() == null || message.getOrderId().isBlank()) {
            // Throwing an exception causes Spring AMQP to NACK the message.
            // Because the queue has a dead-letter exchange configured, the message
            // is forwarded to the DLQ rather than being re-queued infinitely.
            throw new IllegalArgumentException(
                    "Received an order message with a blank orderId: " + message);
        }

        // Process the order (simulated here by logging and storing in memory)
        processOrder(message);

        log.info("Successfully processed order: orderId={}, messageId={}",
                message.getOrderId(), message.getMessageId());
    }

    // ── Internal processing logic ──────────────────────────────────────────────────

    /**
     * Simulate order processing by logging and recording the message.
     *
     * <p>This method encapsulates the "business logic" of the consumer.
     * Having it as a separate method makes it easy to unit-test the processing
     * logic without going through the full AMQP listener stack.
     *
     * @param message the validated order message to process
     */
    public void processOrder(OrderMessage message) {
        // Increment the total count of processed messages
        long count = messageCount.incrementAndGet();

        // Record the processed message in the in-memory log
        ProcessedMessage record = new ProcessedMessage(
                message.getMessageId(),
                message.getOrderId(),
                message.getProduct(),
                message.getQuantity(),
                Instant.now()  // processedAt: the moment this consumer processed it
        );

        synchronized (processedMessages) {
            processedMessages.add(record);
            // Evict oldest entries to keep the list bounded
            while (processedMessages.size() > MAX_STORED_MESSAGES) {
                processedMessages.remove(0);
            }
        }

        log.debug("Processed message #{}: {}", count, record);
    }

    // ── Accessors for the REST API and tests ───────────────────────────────────────

    /**
     * Returns the total number of messages successfully processed since startup.
     *
     * @return the message count as a {@code long}
     */
    public long getMessageCount() {
        return messageCount.get();
    }

    /**
     * Returns an unmodifiable snapshot of the in-memory log of processed messages.
     *
     * <p>The list is capped at {@link #MAX_STORED_MESSAGES} entries. Callers
     * receive a copy so they cannot mutate the internal state.
     *
     * @return an unmodifiable view of the processed message log
     */
    public List<ProcessedMessage> getProcessedMessages() {
        synchronized (processedMessages) {
            // Return a copy to prevent external mutation
            return Collections.unmodifiableList(new ArrayList<>(processedMessages));
        }
    }

    /**
     * Clears the in-memory message log and resets the counter.
     *
     * <p>Primarily used in tests to reset state between test methods without
     * restarting the application context.
     */
    public void reset() {
        messageCount.set(0);
        synchronized (processedMessages) {
            processedMessages.clear();
        }
    }

    // ── Inner record: ProcessedMessage ────────────────────────────────────────────

    /**
     * Immutable snapshot of a successfully processed message, stored in the log.
     *
     * <p>Using a {@code record} (Java 16+) here gives us an immutable value
     * object with auto-generated constructor, getters, {@code equals},
     * {@code hashCode}, and {@code toString} — no boilerplate needed.
     *
     * @param messageId   the original message UUID (from the producer)
     * @param orderId     the business order identifier
     * @param product     the product name
     * @param quantity    the number of units
     * @param processedAt the instant this consumer finished processing the message
     */
    public record ProcessedMessage(
            String messageId,
            String orderId,
            String product,
            int quantity,
            Instant processedAt
    ) {}
}
