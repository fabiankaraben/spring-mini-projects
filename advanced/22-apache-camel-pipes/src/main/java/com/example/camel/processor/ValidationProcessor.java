package com.example.camel.processor;

import com.example.camel.domain.Order;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pipeline Stage 1 — Validation Filter.
 *
 * <p>This processor implements the <em>Filter</em> role in the Pipes and Filters EIP.
 * It inspects the inbound {@link Order} and rejects it by throwing an
 * {@link IllegalArgumentException} if any mandatory field is missing or logically invalid.
 *
 * <p>Camel's {@code onException(IllegalArgumentException.class)} clause in the main route
 * catches that exception and routes the message to the dead-letter channel instead of
 * propagating it to the caller as a 500 error.
 *
 * <p>When validation passes, the processor stamps the {@code receivedAt} timestamp and
 * advances the {@code stage} label so that downstream processors and log statements can
 * identify which step the message is at.
 *
 * <h3>Validation rules</h3>
 * <ul>
 *   <li>{@code orderId}    — must not be null or blank</li>
 *   <li>{@code customerId} — must not be null or blank</li>
 *   <li>{@code productName}— must not be null or blank</li>
 *   <li>{@code unitPrice}  — must be a positive value</li>
 *   <li>{@code quantity}   — must be at least 1</li>
 * </ul>
 */
@Component
public class ValidationProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(ValidationProcessor.class);

    /**
     * Validates the {@link Order} in the Camel message body.
     *
     * @param exchange The Camel exchange carrying the order as its body.
     * @throws IllegalArgumentException if any validation rule is violated.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        // Retrieve the Order object from the Camel exchange body.
        // At this point it was deserialized from the JSON REST request body.
        Order order = exchange.getMessage().getBody(Order.class);

        log.debug("Validating order: {}", order);

        // ── Mandatory string fields ───────────────────────────────────────────
        validateNotBlank(order.getOrderId(), "orderId");
        validateNotBlank(order.getCustomerId(), "customerId");
        validateNotBlank(order.getProductName(), "productName");

        // ── Numeric constraints ───────────────────────────────────────────────
        if (order.getUnitPrice() == null || order.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "unitPrice must be a positive value; received: " + order.getUnitPrice());
        }

        if (order.getQuantity() < 1) {
            throw new IllegalArgumentException(
                    "quantity must be at least 1; received: " + order.getQuantity());
        }

        // ── Stamp timestamp and advance stage label ───────────────────────────
        order.setReceivedAt(Instant.now());
        order.setStage("VALIDATED");

        // Put the enriched order back onto the exchange body so the next stage
        // in the pipeline receives the updated object.
        exchange.getMessage().setBody(order);

        log.info("Order validated [orderId={}]", order.getOrderId());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank; received: '" + value + "'");
        }
    }
}
