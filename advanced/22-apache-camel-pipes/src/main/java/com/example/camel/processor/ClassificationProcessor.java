package com.example.camel.processor;

import com.example.camel.config.AppProperties;
import com.example.camel.domain.Order;
import com.example.camel.domain.OrderPriority;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pipeline Stage 3 — Classification Filter.
 *
 * <p>This processor applies a business rule to assign a {@link OrderPriority} to the enriched
 * order.  The priority drives the dispatch stage: high-value orders are sent to a dedicated
 * priority queue so that downstream consumers can process them first.
 *
 * <h3>Classification rule</h3>
 * <ul>
 *   <li>If {@code totalAmount ≥ priorityThreshold} (configured via
 *       {@code app.pipeline.priority-threshold}) → {@link OrderPriority#PRIORITY}</li>
 *   <li>Otherwise → {@link OrderPriority#STANDARD}</li>
 * </ul>
 *
 * <p>The threshold is externalised to {@code application.yml} so it can be tuned per
 * environment without recompiling the application.
 *
 * <p>After classification the {@code stage} label is advanced to {@code "CLASSIFIED"}.
 */
@Component
public class ClassificationProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(ClassificationProcessor.class);

    /**
     * Application properties; we read {@code app.pipeline.priority-threshold} from here.
     */
    private final AppProperties props;

    public ClassificationProcessor(AppProperties props) {
        this.props = props;
    }

    /**
     * Classifies the order as PRIORITY or STANDARD and sets the {@code priority} field.
     *
     * @param exchange The Camel exchange whose body is an enriched {@link Order}.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        Order order = exchange.getMessage().getBody(Order.class);

        log.debug("Classifying order [orderId={}, totalAmount={}]",
                order.getOrderId(), order.getTotalAmount());

        // Compare totalAmount (computed in the enrichment stage) against the threshold.
        // BigDecimal.compareTo is used to avoid floating-point precision issues.
        BigDecimal threshold = BigDecimal.valueOf(props.getPipeline().getPriorityThreshold());
        boolean isPriority = order.getTotalAmount().compareTo(threshold) >= 0;

        // Assign the priority and advance the stage label.
        OrderPriority priority = isPriority ? OrderPriority.PRIORITY : OrderPriority.STANDARD;
        order.setPriority(priority);
        order.setStage("CLASSIFIED");

        exchange.getMessage().setBody(order);

        log.info("Order classified [orderId={}, priority={}, threshold={}]",
                order.getOrderId(), priority, threshold);
    }
}
