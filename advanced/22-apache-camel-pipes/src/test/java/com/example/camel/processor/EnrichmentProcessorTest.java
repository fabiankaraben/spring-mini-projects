package com.example.camel.processor;

import com.example.camel.config.AppProperties;
import com.example.camel.domain.Order;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EnrichmentProcessor}.
 *
 * <p>All tests are pure JUnit 5 — no Spring context, no Camel route started.
 * We manually create a minimal {@link AppProperties} with a known VAT rate and inject
 * it into the processor so we can predict the exact computed values.
 *
 * <h3>What we verify</h3>
 * <ul>
 *   <li>Total amount calculation (unitPrice × quantity)</li>
 *   <li>VAT amount calculation (total × vatRate)</li>
 *   <li>Region derivation from customerId prefix</li>
 *   <li>Stage label advancement</li>
 * </ul>
 */
@DisplayName("EnrichmentProcessor")
class EnrichmentProcessorTest {

    private EnrichmentProcessor processor;
    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        // Create AppProperties with a known VAT rate so tests are deterministic.
        AppProperties props = new AppProperties();
        props.getPipeline().setVatRate(0.20); // 20 % for easy mental arithmetic

        processor = new EnrichmentProcessor(props);

        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Exchange exchangeWithOrder(String customerId, BigDecimal unitPrice, int qty) {
        Order order = new Order("ORD-E01", customerId, "Widget", unitPrice, qty);
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(order);
        return exchange;
    }

    // ── Total amount ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Total amount = unitPrice × quantity (rounded to 2 dp)")
    void totalAmountIsComputedCorrectly() throws Exception {
        // 9.99 × 3 = 29.97
        Exchange exchange = exchangeWithOrder("EU-CUST-1", new BigDecimal("9.99"), 3);
        processor.process(exchange);

        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("29.97");
    }

    // ── VAT amount ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAT amount = total × 0.20 (rounded to 2 dp)")
    void vatAmountIsComputedCorrectly() throws Exception {
        // 10.00 × 5 = 50.00; VAT 20% = 10.00
        Exchange exchange = exchangeWithOrder("EU-CUST-2", new BigDecimal("10.00"), 5);
        processor.process(exchange);

        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getVatAmount()).isEqualByComparingTo("10.00");
    }

    // ── Region derivation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("customerId starting with 'EU' derives region EU")
    void euPrefixGivesEuRegion() throws Exception {
        Exchange exchange = exchangeWithOrder("EU-CUST-42", new BigDecimal("1.00"), 1);
        processor.process(exchange);

        assertThat(exchange.getMessage().getBody(Order.class).getRegion()).isEqualTo("EU");
    }

    @Test
    @DisplayName("customerId starting with 'US' derives region US")
    void usPrefixGivesUsRegion() throws Exception {
        Exchange exchange = exchangeWithOrder("US-CUST-99", new BigDecimal("1.00"), 1);
        processor.process(exchange);

        assertThat(exchange.getMessage().getBody(Order.class).getRegion()).isEqualTo("US");
    }

    @Test
    @DisplayName("customerId starting with 'AP' derives region APAC")
    void apPrefixGivesApacRegion() throws Exception {
        Exchange exchange = exchangeWithOrder("AP-CUST-7", new BigDecimal("1.00"), 1);
        processor.process(exchange);

        assertThat(exchange.getMessage().getBody(Order.class).getRegion()).isEqualTo("APAC");
    }

    @Test
    @DisplayName("Unknown customerId prefix derives region UNKNOWN")
    void unknownPrefixGivesUnknownRegion() throws Exception {
        Exchange exchange = exchangeWithOrder("ZZ-CUST-0", new BigDecimal("1.00"), 1);
        processor.process(exchange);

        assertThat(exchange.getMessage().getBody(Order.class).getRegion()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Very short customerId (< 2 chars) derives region UNKNOWN")
    void shortCustomerIdGivesUnknownRegion() {
        // Test the helper method directly since it is package-private.
        assertThat(processor.deriveRegion("E")).isEqualTo("UNKNOWN");
        assertThat(processor.deriveRegion("")).isEqualTo("UNKNOWN");
        assertThat(processor.deriveRegion(null)).isEqualTo("UNKNOWN");
    }

    // ── Stage label ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Stage is advanced to ENRICHED after processing")
    void stageIsSetToEnriched() throws Exception {
        Exchange exchange = exchangeWithOrder("EU-CUST-1", new BigDecimal("5.00"), 2);
        processor.process(exchange);

        assertThat(exchange.getMessage().getBody(Order.class).getStage()).isEqualTo("ENRICHED");
    }
}
