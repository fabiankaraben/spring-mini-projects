package com.example.tracing.controller;

import com.example.tracing.model.InventoryResponse;
import com.example.tracing.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for inventory stock queries.
 *
 * <p>This controller is called by the Feign client {@code InventoryClient} from
 * within the same application. In a real microservices architecture it would live
 * in a separate process, but running it in the same JVM keeps this mini-project
 * self-contained while still demonstrating genuine context propagation.
 *
 * <p><b>Trace context extraction:</b>
 * When the Feign client in {@code OrderService} makes a {@code GET /inventory/{id}}
 * call, Brave injects these headers into the outbound HTTP request:
 * <pre>
 *   X-B3-TraceId:     &lt;the existing trace ID from the order request&gt;
 *   X-B3-SpanId:      &lt;a new span ID for the Feign HTTP call&gt;
 *   X-B3-ParentSpanId: &lt;the process-order span ID&gt;
 *   X-B3-Sampled:     1
 * </pre>
 * Spring Boot's auto-configured {@code ObservationFilter} extracts these headers
 * on the receiving side and resumes the trace — creating a child span
 * {@code "http GET /inventory/{productId}"} under the same traceId. This is
 * what produces the waterfall view in Zipkin.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Returns the current stock level for a product.
     *
     * <p>The Micrometer Observation instrumentation wraps this handler in a span.
     * The {@link InventoryService} further creates a named child span
     * {@code "inventory-check"} inside this handler, producing one more level
     * of depth in the Zipkin waterfall.
     *
     * @param productId the product whose stock level is being queried
     * @return current inventory state (available, reserved) with trace context
     */
    @GetMapping("/{productId}")
    public InventoryResponse checkInventory(@PathVariable String productId) {
        log.info("Inventory check request received [productId={}]", productId);
        return inventoryService.checkStock(productId);
    }
}
