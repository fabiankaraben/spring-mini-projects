package com.example.camel.processor;

import com.example.camel.domain.Order;
import com.example.camel.domain.OrderPriority;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pipeline Stage 4 — Dispatch Filter.
 *
 * <p>This processor decides which JMS destination the classified order should be sent to.
 * It uses the Camel {@code SLIP_ENDPOINT} header (or a custom header) to signal to the
 * route's {@code .toD()} step which queue endpoint to use.
 *
 * <p>Rather than routing to a hard-coded JMS destination, we set a Camel exchange header
 * ({@code CamelJmsDestinationName}) so the subsequent {@code .toD("jms:queue:${header...}")}
 * step resolves the destination dynamically at runtime.  This demonstrates the
 * <em>Dynamic Router</em> pattern working together with Pipes and Filters.
 *
 * <h3>Routing rules</h3>
 * <ul>
 *   <li>{@link OrderPriority#PRIORITY}  → JMS queue {@code orders.priority}</li>
 *   <li>{@link OrderPriority#STANDARD}  → JMS queue {@code orders.standard}</li>
 * </ul>
 *
 * <p>After dispatch setup the {@code stage} label is advanced to {@code "DISPATCHED"}.
 */
@Component
public class DispatchProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(DispatchProcessor.class);

    /** Camel exchange header used by the JMS component to override the destination name. */
    public static final String HEADER_JMS_DESTINATION = "CamelJmsDestinationName";

    /** JMS queue name for high-value orders. */
    public static final String QUEUE_PRIORITY = "orders.priority";

    /** JMS queue name for standard orders. */
    public static final String QUEUE_STANDARD = "orders.standard";

    /**
     * Sets the {@code CamelJmsDestinationName} header to the queue determined by the
     * order's {@link OrderPriority}.
     *
     * @param exchange The Camel exchange whose body is a classified {@link Order}.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        Order order = exchange.getMessage().getBody(Order.class);

        // Choose the target queue name based on the order priority assigned in stage 3.
        String destination = (order.getPriority() == OrderPriority.PRIORITY)
                ? QUEUE_PRIORITY
                : QUEUE_STANDARD;

        // Setting this Camel header tells the JMS component which queue to send to.
        // The route uses .toD("jms:queue:" + header) to resolve it at runtime.
        exchange.getMessage().setHeader(HEADER_JMS_DESTINATION, destination);

        // Advance stage label for traceability.
        order.setStage("DISPATCHED");
        exchange.getMessage().setBody(order);

        log.info("Order dispatched [orderId={}, priority={}, queue={}]",
                order.getOrderId(), order.getPriority(), destination);
    }
}
