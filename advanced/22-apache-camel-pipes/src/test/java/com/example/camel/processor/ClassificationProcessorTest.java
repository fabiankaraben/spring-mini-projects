package com.example.camel.processor;

import com.example.camel.config.AppProperties;
import com.example.camel.domain.Order;
import com.example.camel.domain.OrderPriority;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClassificationProcessor}.
 *
 * <p>Pure JUnit 5 — no Spring context, no Camel route started.
 * We set a known priority threshold of 300.0 in {@link AppProperties} and test orders
 * above, at, and below that threshold.
 *
 * <h3>Rules under test</h3>
 * <ul>
 *   <li>totalAmount &ge; threshold → {@link OrderPriority#PRIORITY}</li>
 *   <li>totalAmount &lt; threshold → {@link OrderPriority#STANDARD}</li>
 *   <li>Stage label must be advanced to {@code "CLASSIFIED"}</li>
 * </ul>
 */
@DisplayName("ClassificationProcessor")
class ClassificationProcessorTest {

    private ClassificationProcessor processor;
    private DefaultCamelContext camelContext;

    /** Known threshold used in all tests. */
    private static final double THRESHOLD = 300.0;

    @BeforeEach
    void setUp() throws Exception {
        AppProperties props = new AppProperties();
        props.getPipeline().setPriorityThreshold(THRESHOLD);

        processor = new ClassificationProcessor(props);

        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Creates an exchange whose body is an Order with totalAmount already set (post-enrichment). */
    private Exchange exchangeWithTotal(BigDecimal totalAmount) {
        Order order = new Order("ORD-C01", "EU-CUST-1", "Widget", new BigDecimal("1.00"), 1);
        order.setTotalAmount(totalAmount);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(order);
        return exchange;
    }

    // ── Classification rules ──────────────────────────────────────────────────

    @Test
    @DisplayName("Order above threshold is classified as PRIORITY")
    void aboveThresholdIsPriority() throws Exception {
        Exchange exchange = exchangeWithTotal(new BigDecimal("500.00")); // > 300
        processor.process(exchange);

        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getPriority()).isEqualTo(OrderPriority.PRIORITY);
    }

    @Test
    @DisplayName("Order exactly at threshold is classified as PRIORITY (inclusive)")
    void exactlyAtThresholdIsPriority() throws Exception {
        Exchange exchange = exchangeWithTotal(new BigDecimal("300.00")); // == 300 → PRIORITY
        processor.process(exchange);

        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getPriority()).isEqualTo(OrderPriority.PRIORITY);
    }

    @Test
    @DisplayName("Order below threshold is classified as STANDARD")
    void belowThresholdIsStandard() throws Exception {
        Exchange exchange = exchangeWithTotal(new BigDecimal("299.99")); // < 300
        processor.process(exchange);

        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getPriority()).isEqualTo(OrderPriority.STANDARD);
    }

    @Test
    @DisplayName("Order with very small amount is classified as STANDARD")
    void verySmallAmountIsStandard() throws Exception {
        Exchange exchange = exchangeWithTotal(new BigDecimal("0.01"));
        processor.process(exchange);

        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getPriority()).isEqualTo(OrderPriority.STANDARD);
    }

    // ── Stage label ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Stage is advanced to CLASSIFIED after processing")
    void stageIsSetToClassified() throws Exception {
        Exchange exchange = exchangeWithTotal(new BigDecimal("100.00"));
        processor.process(exchange);

        assertThat(exchange.getMessage().getBody(Order.class).getStage()).isEqualTo("CLASSIFIED");
    }
}
