package com.example.camel.processor;

import com.example.camel.domain.Order;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ValidationProcessor}.
 *
 * <p>Tests run without Spring context — we manually construct a lightweight
 * {@link DefaultCamelContext} and {@link DefaultExchange} to drive the processor.
 * This keeps tests fast (no container startup) and focused purely on validation logic.
 *
 * <p>Pattern used:
 * <ol>
 *   <li>Create a {@code DefaultCamelContext} — required by {@code DefaultExchange}.</li>
 *   <li>Create a {@code DefaultExchange} and set the body to an {@link Order}.</li>
 *   <li>Call {@code processor.process(exchange)}.</li>
 *   <li>Assert the exchange body or that an exception was thrown.</li>
 * </ol>
 */
@DisplayName("ValidationProcessor")
class ValidationProcessorTest {

    /** System under test — no Spring injection needed; it has no dependencies. */
    private ValidationProcessor processor;

    /** Lightweight Camel context used only to construct DefaultExchange. */
    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        processor = new ValidationProcessor();
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Creates a valid {@link Order} that should pass all validation rules.
     * Individual tests then mutate a single field to trigger a specific failure.
     */
    private Order validOrder() {
        return new Order("ORD-001", "EU-CUST-1", "Widget", new BigDecimal("9.99"), 2);
    }

    private Exchange exchangeWith(Order order) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(order);
        return exchange;
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid order passes validation and stage is set to VALIDATED")
    void validOrderPassesValidation() throws Exception {
        Order order = validOrder();
        Exchange exchange = exchangeWith(order);

        processor.process(exchange);

        // The processor should advance the stage label.
        Order result = exchange.getMessage().getBody(Order.class);
        assertThat(result.getStage()).isEqualTo("VALIDATED");
        // receivedAt should be stamped by the processor.
        assertThat(result.getReceivedAt()).isNotNull();
    }

    // ── Null / blank field failures ───────────────────────────────────────────

    @Test
    @DisplayName("Null orderId throws IllegalArgumentException")
    void nullOrderIdThrows() {
        Order order = validOrder();
        order.setOrderId(null);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("Blank orderId throws IllegalArgumentException")
    void blankOrderIdThrows() {
        Order order = validOrder();
        order.setOrderId("   ");

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("Null customerId throws IllegalArgumentException")
    void nullCustomerIdThrows() {
        Order order = validOrder();
        order.setCustomerId(null);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    @DisplayName("Null productName throws IllegalArgumentException")
    void nullProductNameThrows() {
        Order order = validOrder();
        order.setProductName(null);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productName");
    }

    // ── Numeric constraint failures ───────────────────────────────────────────

    @Test
    @DisplayName("Zero unitPrice throws IllegalArgumentException")
    void zeroUnitPriceThrows() {
        Order order = validOrder();
        order.setUnitPrice(BigDecimal.ZERO);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    @DisplayName("Negative unitPrice throws IllegalArgumentException")
    void negativeUnitPriceThrows() {
        Order order = validOrder();
        order.setUnitPrice(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    @DisplayName("Null unitPrice throws IllegalArgumentException")
    void nullUnitPriceThrows() {
        Order order = validOrder();
        order.setUnitPrice(null);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice");
    }

    @Test
    @DisplayName("Zero quantity throws IllegalArgumentException")
    void zeroQuantityThrows() {
        Order order = validOrder();
        order.setQuantity(0);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("Negative quantity throws IllegalArgumentException")
    void negativeQuantityThrows() {
        Order order = validOrder();
        order.setQuantity(-5);

        assertThatThrownBy(() -> processor.process(exchangeWith(order)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }
}
