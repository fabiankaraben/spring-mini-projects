package com.example.cloudstream.stream;

import com.example.cloudstream.events.OrderPlacedEvent;
import com.example.cloudstream.events.OrderProcessedEvent;
import com.example.cloudstream.events.OrderRejectedEvent;
import com.example.cloudstream.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Spring Cloud Stream functional beans for the order event pipeline.
 *
 * <p>Spring Cloud Stream's <em>functional programming model</em> maps standard
 * Java {@link Supplier}, {@link Function}, and {@link Consumer} beans to Kafka
 * topics using configuration in {@code application.yml}:
 *
 * <pre>
 *   Bean name                 Binding type     Kafka topic
 *   ─────────────────────     ────────────     ────────────────────
 *   orderSupplier             Supplier         orders              (outbound)
 *   orderProcessor            Function         orders              (inbound)
 *                                              orders-processed    (outbound)
 *   notificationConsumer      Consumer         orders-processed    (inbound)
 *   rejectionLogger           Consumer         orders-rejected     (inbound)
 * </pre>
 *
 * <p>Spring Cloud Stream <strong>automatically</strong>:
 * <ul>
 *   <li>Discovers these beans by type ({@code Supplier}, {@code Function}, {@code Consumer}).</li>
 *   <li>Creates Kafka producer/consumer clients for each binding.</li>
 *   <li>Serializes outbound messages as JSON and deserializes inbound messages from JSON.</li>
 *   <li>Manages Kafka consumer group offsets and retries.</li>
 * </ul>
 *
 * <p>You never write KafkaTemplate or @KafkaListener code — just plain Java functions.
 */
@Configuration
public class OrderStreamFunctions {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamFunctions.class);

    /**
     * The minimum delivery estimate for a single-item order (in days).
     * Multi-item orders add one extra day per 5 additional units.
     */
    private static final int BASE_DELIVERY_DAYS = 3;

    private final OrderService orderService;

    public OrderStreamFunctions(OrderService orderService) {
        this.orderService = orderService;
    }

    // =========================================================================
    // 1. orderSupplier — Supplier<Message<OrderPlacedEvent>>
    // =========================================================================

    /**
     * Spring Cloud Stream <strong>Supplier</strong> bean.
     *
     * <p>A {@link Supplier} in Spring Cloud Stream is an <em>outbound source</em>.
     * Spring polls this Supplier on a configurable schedule (default: 1 second)
     * and, if the returned value is non-null, publishes it as a Kafka message.
     *
     * <p>We wrap the payload in a {@link Message} so we can set a custom Kafka
     * message key (the order UUID). Without a custom key, Kafka assigns a random
     * partition. Setting the key ensures that all events for the same order always
     * land on the same partition, preserving ordering per order.
     *
     * <p>Binding name: {@code orderSupplier-out-0}
     * → mapped to Kafka topic {@code orders} in {@code application.yml}.
     *
     * <p>How the hand-off works:
     * <pre>
     *   REST handler → orderService.placeOrder() → pendingEvents queue
     *                          ↑
     *   Spring Cloud Stream → orderSupplier.get() → pendingEvents.poll() → Kafka
     * </pre>
     *
     * @return a {@link Supplier} that drains the pending events queue
     */
    @Bean
    public Supplier<Message<OrderPlacedEvent>> orderSupplier() {
        return () -> {
            // Poll the next event from the in-memory queue (returns null if empty).
            // Returning null tells Spring Cloud Stream: "nothing to send this cycle."
            OrderPlacedEvent event = orderService.pollNextEvent();

            if (event == null) {
                // No pending events — skip this polling cycle
                return null;
            }

            log.info("[orderSupplier] Publishing OrderPlacedEvent for orderId={}",
                    event.orderId());

            // Wrap in a Spring Message for the Kafka binder.
            // No custom message key — Kafka assigns partitions using its default
            // round-robin strategy, which is sufficient for this demo.
            return MessageBuilder
                    .withPayload(event)
                    .build();
        };
    }

    // =========================================================================
    // 2. orderProcessor — Function<OrderPlacedEvent, Message<?>>
    // =========================================================================

    /**
     * Spring Cloud Stream <strong>Function</strong> bean.
     *
     * <p>A {@link Function} in Spring Cloud Stream is both:
     * <ul>
     *   <li>A <em>consumer</em> of the inbound binding ({@code orders} topic).</li>
     *   <li>A <em>producer</em> of the outbound binding ({@code orders-processed} or
     *       {@code orders-rejected} topic).</li>
     * </ul>
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Validate the event (price, quantity, product ID).</li>
     *   <li>If invalid: publish an {@link OrderRejectedEvent} to {@code orders-rejected}
     *       and update the order status to REJECTED.</li>
     *   <li>If valid: enrich the event (add delivery estimate, summary string),
     *       publish an {@link OrderProcessedEvent} to {@code orders-processed},
     *       and update the order status to PROCESSING.</li>
     * </ol>
     *
     * <p>Binding names:
     * <ul>
     *   <li>Inbound:  {@code orderProcessor-in-0}  → topic {@code orders}</li>
     *   <li>Outbound: {@code orderProcessor-out-0} → topic {@code orders-processed}</li>
     * </ul>
     *
     * <p>Note: We return {@code Message<?>} (not a union type) because Spring Cloud
     * Stream routes the output to a single outbound binding. We use a custom header
     * ({@code spring.cloud.stream.sendto.destination}) to dynamically override which
     * topic the message is routed to (processed vs rejected).
     *
     * @return a {@link Function} that validates and enriches an {@link OrderPlacedEvent}
     */
    @Bean
    public Function<OrderPlacedEvent, Message<?>> orderProcessor() {
        return event -> {
            log.info("[orderProcessor] Received OrderPlacedEvent for orderId={}", event.orderId());

            // --- Validation ---
            String validationError = validate(event);

            if (validationError != null) {
                // Order is invalid — publish a rejection event
                log.warn("[orderProcessor] Order {} rejected: {}", event.orderId(), validationError);

                OrderRejectedEvent rejection = new OrderRejectedEvent(
                        event.orderId(),
                        event.customerId(),
                        event.productId(),
                        validationError,
                        Instant.now()
                );

                // Update the in-memory order status to REJECTED
                orderService.markRejected(event.orderId(), validationError);

                // Route to the "orders-rejected" topic using the dynamic destination header.
                // Spring Cloud Stream reads this header and overrides the default outbound binding.
                return MessageBuilder
                        .withPayload(rejection)
                        .setHeader("spring.cloud.stream.sendto.destination", "orders-rejected")
                        .build();
            }

            // --- Enrichment ---
            int estimatedDays = computeDeliveryDays(event.quantity());
            String summary = buildSummary(event, estimatedDays);

            OrderProcessedEvent processed = new OrderProcessedEvent(
                    event.orderId(),
                    event.customerId(),
                    event.productId(),
                    event.quantity(),
                    event.totalPrice(),
                    event.placedAt(),
                    Instant.now(),
                    estimatedDays,
                    summary
            );

            // Update the in-memory order status to PROCESSING
            orderService.markProcessing(event.orderId());

            log.info("[orderProcessor] Order {} enriched: estimatedDays={}, summary={}",
                    event.orderId(), estimatedDays, summary);

            // Publish to the default outbound binding (orders-processed)
            return MessageBuilder
                    .withPayload(processed)
                    .build();
        };
    }

    // =========================================================================
    // 3. notificationConsumer — Consumer<OrderProcessedEvent>
    // =========================================================================

    /**
     * Spring Cloud Stream <strong>Consumer</strong> bean for successfully processed orders.
     *
     * <p>A {@link Consumer} in Spring Cloud Stream is a pure <em>inbound sink</em>.
     * It receives messages from the bound Kafka topic and performs a side-effect
     * (in this case, logging a simulated notification).
     *
     * <p>In a real system this would:
     * <ul>
     *   <li>Send an email via AWS SES or SendGrid.</li>
     *   <li>Send an SMS via Twilio.</li>
     *   <li>Push a mobile notification via Firebase Cloud Messaging.</li>
     * </ul>
     *
     * <p>Binding name: {@code notificationConsumer-in-0}
     * → mapped to Kafka topic {@code orders-processed} in {@code application.yml}.
     *
     * @return a {@link Consumer} that logs simulated order confirmation notifications
     */
    @Bean
    public Consumer<OrderProcessedEvent> notificationConsumer() {
        return event -> {
            // Simulate sending an order confirmation notification
            log.info("[notificationConsumer] ============================");
            log.info("[notificationConsumer] ORDER CONFIRMATION NOTIFICATION");
            log.info("[notificationConsumer] To: customer '{}'", event.customerId());
            log.info("[notificationConsumer] {}", event.summary());
            log.info("[notificationConsumer] Estimated delivery: {} days", event.estimatedDeliveryDays());
            log.info("[notificationConsumer] ============================");

            // Update the in-memory order status to NOTIFIED
            orderService.markNotified(event.orderId());
        };
    }

    // =========================================================================
    // 4. rejectionLogger — Consumer<OrderRejectedEvent>
    // =========================================================================

    /**
     * Spring Cloud Stream <strong>Consumer</strong> bean for rejected orders.
     *
     * <p>Consumes messages from the {@code orders-rejected} topic and logs
     * a simulated rejection notification to the customer.
     *
     * <p>Binding name: {@code rejectionLogger-in-0}
     * → mapped to Kafka topic {@code orders-rejected} in {@code application.yml}.
     *
     * @return a {@link Consumer} that logs simulated order rejection notifications
     */
    @Bean
    public Consumer<OrderRejectedEvent> rejectionLogger() {
        return event -> {
            log.warn("[rejectionLogger] ============================");
            log.warn("[rejectionLogger] ORDER REJECTION NOTIFICATION");
            log.warn("[rejectionLogger] To: customer '{}'", event.customerId());
            log.warn("[rejectionLogger] Order {} for product '{}' was rejected: {}",
                    event.orderId(), event.productId(), event.reason());
            log.warn("[rejectionLogger] ============================");
        };
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Validates an {@link OrderPlacedEvent}.
     *
     * @param event the event to validate
     * @return a non-null error message if validation fails, or {@code null} if valid
     */
    private String validate(OrderPlacedEvent event) {
        if (event.totalPrice() == null || event.totalPrice().signum() <= 0) {
            return "Total price must be positive";
        }
        if (event.quantity() <= 0) {
            return "Quantity must be positive";
        }
        if (event.productId() == null || event.productId().isBlank()) {
            return "Product ID must not be blank";
        }
        return null; // valid
    }

    /**
     * Computes the estimated delivery time in days.
     *
     * <p>Formula: base 3 days + 1 extra day for every 5 additional units.
     * Examples:
     * <ul>
     *   <li>quantity 1–5  → 3 days</li>
     *   <li>quantity 6–10 → 4 days</li>
     *   <li>quantity 11+  → 5+ days</li>
     * </ul>
     *
     * @param quantity number of units ordered
     * @return estimated delivery days
     */
    private int computeDeliveryDays(int quantity) {
        return BASE_DELIVERY_DAYS + (quantity - 1) / 5;
    }

    /**
     * Builds a human-readable order summary for the notification message.
     *
     * @param event         the placed event
     * @param deliveryDays  computed delivery estimate
     * @return formatted summary string
     */
    private String buildSummary(OrderPlacedEvent event, int deliveryDays) {
        return String.format(
                "Your order of %d x '%s' for $%.2f has been confirmed. " +
                "Expected delivery in %d business days.",
                event.quantity(),
                event.productId(),
                event.totalPrice(),
                deliveryDays
        );
    }
}
