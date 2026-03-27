package com.example.camel.processor;

import com.example.camel.domain.Order;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Pipeline Stage 5 — Persistence Filter.
 *
 * <p>This processor is the final stage before the message is handed off to the Camel
 * file component for writing to disk.  It stamps the {@code processedAt} timestamp,
 * advances the {@code stage} label to {@code "COMPLETED"}, and sets the
 * {@code CamelFileName} header so the file component uses a meaningful, unique filename.
 *
 * <h3>Filename convention</h3>
 * <pre>
 *   order-{orderId}-{epochMillis}.json
 * </pre>
 * <p>Using the epoch millis suffix prevents filename collisions when the same orderId is
 * submitted more than once (e.g., during a retry).
 *
 * <p>The actual file write is performed by the Camel file endpoint configured in the route
 * ({@code file:${app.output.dir}}).  This processor only prepares the exchange; it does
 * not touch the file system directly, keeping the single-responsibility principle intact.
 */
@Component
public class PersistenceProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(PersistenceProcessor.class);

    /**
     * Camel header that the file component reads to determine the output filename.
     * Setting it here lets us compose a dynamic, meaningful name.
     */
    public static final String HEADER_CAMEL_FILE_NAME = "CamelFileName";

    /**
     * Stamps the completion timestamp, advances the stage label, and sets the output
     * filename header so the file component can write a uniquely-named JSON file.
     *
     * @param exchange The Camel exchange whose body is a dispatched {@link Order}.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        Order order = exchange.getMessage().getBody(Order.class);

        // Stamp the pipeline completion timestamp.
        order.setProcessedAt(Instant.now());
        order.setStage("COMPLETED");

        // Compose a collision-resistant filename for the file component.
        // Format: order-<orderId>-<epochMillis>.json
        String fileName = "order-" + order.getOrderId() + "-" + System.currentTimeMillis() + ".json";
        exchange.getMessage().setHeader(HEADER_CAMEL_FILE_NAME, fileName);

        exchange.getMessage().setBody(order);

        log.info("Order pipeline completed [orderId={}, file={}]",
                order.getOrderId(), fileName);
    }
}
