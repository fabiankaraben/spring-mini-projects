package com.example.camel.domain;

/**
 * Enumeration of order priority levels assigned during the classification stage of the pipeline.
 *
 * <p>The classification processor compares the order's {@code totalAmount} against a configurable
 * threshold.  Orders at or above the threshold are marked {@code PRIORITY}; all others are
 * {@code STANDARD}.
 *
 * <p>The priority value determines which JMS queue the dispatch processor sends the order to:
 * <ul>
 *   <li>{@code PRIORITY} → {@code orders.priority} queue (processed first by downstream consumers)</li>
 *   <li>{@code STANDARD} → {@code orders.standard} queue</li>
 * </ul>
 */
public enum OrderPriority {

    /**
     * High-value order routed to the priority processing queue.
     * Downstream consumers should handle these orders before STANDARD ones.
     */
    PRIORITY,

    /**
     * Normal order routed to the standard processing queue.
     */
    STANDARD
}
