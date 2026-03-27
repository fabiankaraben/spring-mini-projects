package com.example.camel.route;

import com.example.camel.config.AppProperties;
import com.example.camel.domain.Order;
import com.example.camel.domain.OrderPriority;
import com.example.camel.processor.ClassificationProcessor;
import com.example.camel.processor.DispatchProcessor;
import com.example.camel.processor.EnrichmentProcessor;
import com.example.camel.processor.PersistenceProcessor;
import com.example.camel.processor.ValidationProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.stereotype.Component;

/**
 * Main Camel route that implements the <em>Pipes and Filters</em> Enterprise Integration Pattern.
 *
 * <p>A {@link RouteBuilder} defines one or more Camel routes by overriding {@link #configure()}.
 * This class defines <strong>three</strong> routes:
 *
 * <ol>
 *   <li><b>order-pipeline</b> — the main pipeline route.  Messages enter from the
 *       {@code direct:orders} endpoint (called by the REST controller) and pass through
 *       five sequential filter stages:
 *       <ol style="list-style-type: lower-alpha">
 *         <li>Validation  — rejects malformed orders to the dead-letter channel</li>
 *         <li>Enrichment  — computes total, VAT, and region</li>
 *         <li>Classification — tags as PRIORITY or STANDARD</li>
 *         <li>Dispatch    — sets JMS destination header</li>
 *         <li>Persistence — stamps completion timestamp, sets filename header</li>
 *       </ol>
 *       After persistence the message is sent to the JMS queue (via {@code toD}) and also
 *       written to the file system.</li>
 *
 *   <li><b>dead-letter-handler</b> — consumes from the {@code seda:dead-letter} endpoint
 *       (an in-memory async queue).  Logs the error and the failed order payload.
 *       In a production system this would also write to a database or send an alert.</li>
 *
 *   <li><b>notification-handler</b> — consumes from {@code seda:notifications}.  Logs a
 *       lightweight audit entry confirming that the order reached the JMS broker.  This
 *       could be extended to call an external notification service.</li>
 * </ol>
 *
 * <h3>Error handling strategy</h3>
 * <p>The {@code onException(IllegalArgumentException.class)} clause intercepts validation
 * failures and routes them to {@code seda:dead-letter} without retrying.  All other
 * exceptions bubble up as HTTP 500 responses.  This keeps the happy path clean.
 *
 * <h3>Camel DSL concepts demonstrated</h3>
 * <ul>
 *   <li>{@code from()} — defines the entry endpoint of a route</li>
 *   <li>{@code process()} — delegates to a {@link org.apache.camel.Processor}</li>
 *   <li>{@code marshal().json()} — serializes the body to JSON using Jackson</li>
 *   <li>{@code toD()} — sends to a dynamically-resolved endpoint URI</li>
 *   <li>{@code to()} — sends to a static endpoint</li>
 *   <li>{@code log()} — emits a log entry at the specified level</li>
 *   <li>{@code onException()} — registers a scoped exception handler</li>
 * </ul>
 */
@Component
public class OrderPipelineRoute extends RouteBuilder {

    // ── Injected pipeline processors ──────────────────────────────────────────

    private final ValidationProcessor     validationProcessor;
    private final EnrichmentProcessor     enrichmentProcessor;
    private final ClassificationProcessor classificationProcessor;
    private final DispatchProcessor       dispatchProcessor;
    private final PersistenceProcessor    persistenceProcessor;

    /** Application properties - used to resolve the output directory URI at startup. */
    private final AppProperties props;

    /**
     * Camel Jackson data format backed by Spring Boot's ObjectMapper (with JavaTimeModule).
     * Injected from the {@link com.example.camel.config.CamelConfig#orderJsonFormat} bean.
     * This is needed to correctly serialize java.time.Instant fields in Order.
     */
    private final JacksonDataFormat orderJsonFormat;

    public OrderPipelineRoute(
            ValidationProcessor     validationProcessor,
            EnrichmentProcessor     enrichmentProcessor,
            ClassificationProcessor classificationProcessor,
            DispatchProcessor       dispatchProcessor,
            PersistenceProcessor    persistenceProcessor,
            AppProperties           props,
            JacksonDataFormat       orderJsonFormat) {
        this.validationProcessor     = validationProcessor;
        this.enrichmentProcessor     = enrichmentProcessor;
        this.classificationProcessor = classificationProcessor;
        this.dispatchProcessor       = dispatchProcessor;
        this.persistenceProcessor    = persistenceProcessor;
        this.props                   = props;
        this.orderJsonFormat         = orderJsonFormat;
    }

    /**
     * Defines all Camel routes for this application.
     *
     * <p>Called automatically by the Camel context on startup.  Every {@code from(...)} call
     * starts a new independent route.
     */
    @Override
    public void configure() throws Exception {

        // ── Global error handler ──────────────────────────────────────────────
        //
        // IllegalArgumentException is thrown by ValidationProcessor when an order
        // fails business-rule checks.  We handle it here so it does NOT propagate
        // as an unhandled exception (which would return HTTP 500).
        //
        // .handled(true)       — tells Camel the exception is fully handled; the
        //                        original route exchange is considered finished.
        // .to("seda:...")      — forwards a copy to the dead-letter SEDA queue.
        // .setBody()           — replaces the body with a human-readable error
        //                        message so callers get a meaningful response.
        onException(IllegalArgumentException.class)
                .handled(true)
                .log(LoggingLevel.WARN, "order-pipeline",
                        "Validation failed for order: ${exception.message}")
                .to("seda:dead-letter")
                .setBody(exchange -> {
                    String msg = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)
                            .getMessage();
                    return "REJECTED: " + msg;
                });

        // =====================================================================
        // Route 1: order-pipeline
        //
        // Entry: direct:orders — called synchronously from OrderController.
        // This is a synchronous in-process call; the caller waits for this route
        // to finish before receiving its HTTP response.
        //
        // Pipeline stages are chained left-to-right via the Camel Java DSL.
        // Each .process() call is a filter; each .to() / .toD() call is a pipe.
        // =====================================================================
        from("direct:orders")
                .routeId("order-pipeline")
                .log(LoggingLevel.INFO, "order-pipeline",
                        "Order entering pipeline [orderId=${body.orderId}]")

                // ── Stage 1: Validation ───────────────────────────────────────
                // Rejects orders with missing/invalid fields.
                // Throws IllegalArgumentException on failure → caught by onException above.
                .process(validationProcessor)
                .log(LoggingLevel.DEBUG, "order-pipeline",
                        "Stage 1 (VALIDATED) complete [orderId=${body.orderId}]")

                // ── Stage 2: Enrichment ───────────────────────────────────────
                // Computes totalAmount, vatAmount, and region from raw order data.
                .process(enrichmentProcessor)
                .log(LoggingLevel.DEBUG, "order-pipeline",
                        "Stage 2 (ENRICHED) complete [orderId=${body.orderId}, total=${body.totalAmount}]")

                // ── Stage 3: Classification ───────────────────────────────────
                // Tags order as PRIORITY or STANDARD based on totalAmount threshold.
                .process(classificationProcessor)
                .log(LoggingLevel.DEBUG, "order-pipeline",
                        "Stage 3 (CLASSIFIED) complete [orderId=${body.orderId}, priority=${body.priority}]")

                // ── Stage 4: Dispatch ─────────────────────────────────────────
                // Sets the CamelJmsDestinationName header so toD() resolves the
                // correct JMS queue dynamically.
                .process(dispatchProcessor)

                // ── Stage 5: Persistence (prep) ───────────────────────────────
                // Stamps processedAt timestamp and sets CamelFileName header.
                .process(persistenceProcessor)

                // ── Pipe: Serialise to JSON ───────────────────────────────────
                // Use the orderJsonFormat bean which wraps Spring Boot's ObjectMapper
                // (with JavaTimeModule) so Instant fields serialize correctly.
                .marshal(orderJsonFormat)

                // ── Pipe: Content-Based Router → JMS queues ───────────────────
                // We use choice() with a predicate on the CamelJmsDestinationName
                // header to route to one of two static JMS queue endpoints.
                //
                // WHY NOT toD()? Using toD("jms:queue:${header...}") triggers
                // Artemis request-reply mode (temporary reply queue), which causes
                // "Timed out waiting to receive cluster topology" in tests.
                // Static endpoints (to("jms:queue:<name>")) use fire-and-forget
                // (InOnly), avoiding the temporary queue entirely.
                // Route to the correct JMS queue using ExchangePattern.InOnly.
                // This is critical: ProducerTemplate.requestBody() in the controller
                // sets the exchange pattern to InOut (request-reply). If InOut reaches
                // the JMS component, Camel creates a temporary reply-to queue and waits
                // for a response from Artemis, causing:
                //   "Timed out waiting to receive cluster topology"
                // Using to(ExchangePattern.InOnly, endpoint) overrides the pattern
                // only for this specific send — the rest of the exchange remains InOut
                // so the controller still gets a response back after JMS dispatch.
                .choice()
                    .when(header(DispatchProcessor.HEADER_JMS_DESTINATION)
                            .isEqualTo(DispatchProcessor.QUEUE_PRIORITY))
                        .to(ExchangePattern.InOnly, "jms:queue:" + DispatchProcessor.QUEUE_PRIORITY)
                        .log(LoggingLevel.INFO, "order-pipeline",
                                "Order sent to PRIORITY queue")
                    .otherwise()
                        .to(ExchangePattern.InOnly, "jms:queue:" + DispatchProcessor.QUEUE_STANDARD)
                        .log(LoggingLevel.INFO, "order-pipeline",
                                "Order sent to STANDARD queue")
                .end()

                // Notify the async notification handler about the completed order.
                .to("seda:notifications")

                // ── Pipe: Write JSON file ─────────────────────────────────────
                // The file component writes to the configured output directory.
                // CamelFileName header (set in PersistenceProcessor) controls the filename.
                .toD("file:" + props.getOutput().getDir())
                .log(LoggingLevel.INFO, "order-pipeline",
                        "Order persisted to file [file=${header.CamelFileName}]");

        // =====================================================================
        // Route 2: dead-letter-handler
        //
        // Entry: seda:dead-letter — an asynchronous in-memory SEDA queue.
        // SEDA (Staged Event-Driven Architecture) decouples the producer (main
        // pipeline or error handler) from the consumer (this route).
        //
        // In a production system this route would:
        //   - Write the failed order to a "dead_letter" database table.
        //   - Send an alert to a monitoring system (PagerDuty, Slack, etc.).
        //   - Expose a retry API to resubmit the corrected order.
        // =====================================================================
        from("seda:dead-letter")
                .routeId("dead-letter-handler")
                .log(LoggingLevel.ERROR, "dead-letter-handler",
                        "Dead-letter received — order failed pipeline. " +
                        "Exception: ${exception.message}. Body: ${body}");

        // =====================================================================
        // Route 3: notification-handler
        //
        // Entry: seda:notifications — another async in-memory SEDA queue.
        // Receives a notification after each order successfully reaches the JMS broker.
        //
        // Keeping notifications on a separate route prevents a slow or failed
        // notification from blocking the main pipeline thread.
        // =====================================================================
        from("seda:notifications")
                .routeId("notification-handler")
                // Convert the marshalled body (InputStreamCache/bytes) back to a String
                // so that the log expression can safely reference ${body} as text.
                .convertBodyTo(String.class)
                .log(LoggingLevel.INFO, "notification-handler",
                        "Notification: order successfully processed and dispatched to JMS.");
    }
}
