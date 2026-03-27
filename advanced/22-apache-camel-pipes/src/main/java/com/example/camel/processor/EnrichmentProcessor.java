package com.example.camel.processor;

import com.example.camel.config.AppProperties;
import com.example.camel.domain.Order;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pipeline Stage 2 — Enrichment Filter.
 *
 * <p>This processor adds computed metadata to the {@link Order} that was not present in the
 * original request.  Enrichment is a common pattern in integration pipelines: the caller
 * provides the minimal required fields and the pipeline fills in derived values so that
 * downstream consumers receive a fully-populated message.
 *
 * <h3>Enrichment steps performed</h3>
 * <ol>
 *   <li><b>Total amount</b> — {@code totalAmount = unitPrice × quantity}</li>
 *   <li><b>VAT amount</b>   — {@code vatAmount = totalAmount × vatRate} (configured via
 *       {@code app.pipeline.vat-rate})</li>
 *   <li><b>Region</b>       — derived from the first two characters of {@code customerId}.
 *       A {@code customerId} starting with "EU" → region "EU"; "US" → "US"; "AP" → "APAC";
 *       everything else → "UNKNOWN".</li>
 * </ol>
 *
 * <p>After enrichment the {@code stage} label is advanced to {@code "ENRICHED"} and the
 * updated order is placed back on the exchange body for the next pipeline stage.
 */
@Component
public class EnrichmentProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentProcessor.class);

    /**
     * Application-wide properties injected by Spring.
     * We read {@code app.pipeline.vat-rate} from here.
     */
    private final AppProperties props;

    public EnrichmentProcessor(AppProperties props) {
        this.props = props;
    }

    /**
     * Enriches the {@link Order} in the exchange body with computed fields.
     *
     * @param exchange The Camel exchange whose body is an {@link Order}.
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        Order order = exchange.getMessage().getBody(Order.class);

        log.debug("Enriching order [orderId={}]", order.getOrderId());

        // ── 1. Compute total amount ───────────────────────────────────────────
        // totalAmount = unitPrice × quantity, rounded to 2 decimal places (currency).
        BigDecimal total = order.getUnitPrice()
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        order.setTotalAmount(total);

        // ── 2. Compute VAT ────────────────────────────────────────────────────
        // vatAmount = totalAmount × vatRate, rounded to 2 decimal places.
        BigDecimal vat = total
                .multiply(BigDecimal.valueOf(props.getPipeline().getVatRate()))
                .setScale(2, RoundingMode.HALF_UP);
        order.setVatAmount(vat);

        // ── 3. Derive region from customerId prefix ───────────────────────────
        // Using a simple prefix convention: EU* → EU, US* → US, AP* → APAC.
        // In a real system this might be a database or external service lookup.
        String region = deriveRegion(order.getCustomerId());
        order.setRegion(region);

        // Advance pipeline stage label.
        order.setStage("ENRICHED");
        exchange.getMessage().setBody(order);

        log.info("Order enriched [orderId={}, total={}, vat={}, region={}]",
                order.getOrderId(), total, vat, region);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Determines the geographic region based on a simple prefix convention on the
     * {@code customerId}.  This simulates a lookup that would normally query a
     * customer service or database.
     *
     * @param customerId The customer identifier from the order.
     * @return Region code: "EU", "US", "APAC", or "UNKNOWN".
     */
    String deriveRegion(String customerId) {
        if (customerId == null || customerId.length() < 2) {
            return "UNKNOWN";
        }
        String prefix = customerId.substring(0, 2).toUpperCase();
        return switch (prefix) {
            case "EU" -> "EU";
            case "US" -> "US";
            case "AP" -> "APAC";
            default   -> "UNKNOWN";
        };
    }
}
