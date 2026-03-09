package com.example.kafkaconsumer.service;

import com.example.kafkaconsumer.domain.OrderEvent;
import com.example.kafkaconsumer.domain.OrderStatus;
import com.example.kafkaconsumer.domain.ProcessedOrderEvent;

import java.util.List;

/**
 * Contract for the order-event processing service.
 *
 * <p>Declaring the service as an interface rather than a concrete class provides
 * two important benefits in this educational project:
 * <ol>
 *   <li><strong>Testability</strong> – Mockito can always create a proxy for an
 *       interface, regardless of the JVM version. Mocking concrete classes requires
 *       bytecode instrumentation that is restricted by the Java module system on
 *       Java 21+ and outright blocked on Java 25. Using an interface sidesteps
 *       this limitation entirely.</li>
 *   <li><strong>Decoupling</strong> – The {@link com.example.kafkaconsumer.listener.OrderEventListener}
 *       and {@link com.example.kafkaconsumer.controller.OrderEventController} depend
 *       on this interface instead of the concrete class. A different implementation
 *       (e.g. one that persists to a database) can be swapped in without changing
 *       either the listener or the controller.</li>
 * </ol>
 */
public interface OrderEventProcessor {

    /**
     * Processes a single {@link OrderEvent} received from the Kafka topic.
     *
     * @param event     the order event deserialised from the Kafka message value
     * @param partition the Kafka partition the message was consumed from
     * @param offset    the offset of the message within the partition
     */
    void process(OrderEvent event, int partition, long offset);

    /**
     * Returns an unmodifiable view of all events processed so far.
     *
     * @return an unmodifiable snapshot of all processed events
     */
    List<ProcessedOrderEvent> getProcessedEvents();

    /**
     * Returns the total number of events that have been processed.
     *
     * @return count of processed events
     */
    int getProcessedCount();

    /**
     * Returns all processed events that match the given {@link OrderStatus}.
     *
     * @param status the status to filter by
     * @return an unmodifiable list of processed events with the given status
     */
    List<ProcessedOrderEvent> getProcessedEventsByStatus(OrderStatus status);
}
