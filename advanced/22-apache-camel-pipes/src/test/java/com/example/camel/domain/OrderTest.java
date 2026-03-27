package com.example.camel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Order} domain object.
 *
 * <p>These tests verify that Order getters/setters, the convenience constructor,
 * and the {@code toString()} representation work as expected.  No Spring context
 * or Camel context is started — tests run as pure JUnit 5.
 *
 * <p>Why test a POJO?  In an educational project it is useful to confirm that
 * the domain object wires up correctly and that computed fields remain null
 * until the pipeline processors populate them.
 */
@DisplayName("Order domain object")
class OrderTest {

    @Test
    @DisplayName("Convenience constructor sets all mandatory fields correctly")
    void constructorSetsMandatoryFields() {
        // Arrange & Act
        Order order = new Order("ORD-001", "EU-CUST-1", "Widget", new BigDecimal("9.99"), 3);

        // Assert each field set via the constructor
        assertThat(order.getOrderId()).isEqualTo("ORD-001");
        assertThat(order.getCustomerId()).isEqualTo("EU-CUST-1");
        assertThat(order.getProductName()).isEqualTo("Widget");
        assertThat(order.getUnitPrice()).isEqualByComparingTo("9.99");
        assertThat(order.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Enriched fields are null until processors set them")
    void enrichedFieldsAreInitiallyNull() {
        // A freshly constructed order has no computed/enriched fields yet.
        Order order = new Order("ORD-001", "EU-CUST-1", "Widget", new BigDecimal("9.99"), 3);

        assertThat(order.getTotalAmount()).isNull();
        assertThat(order.getVatAmount()).isNull();
        assertThat(order.getRegion()).isNull();
        assertThat(order.getPriority()).isNull();
        assertThat(order.getReceivedAt()).isNull();
        assertThat(order.getProcessedAt()).isNull();
        assertThat(order.getStage()).isNull();
    }

    @Test
    @DisplayName("Setters update all fields and getters return updated values")
    void settersAndGettersWorkCorrectly() {
        Order order = new Order();
        Instant now = Instant.now();

        order.setOrderId("ORD-002");
        order.setCustomerId("US-CUST-2");
        order.setProductName("Gadget");
        order.setUnitPrice(new BigDecimal("49.95"));
        order.setQuantity(10);
        order.setTotalAmount(new BigDecimal("499.50"));
        order.setVatAmount(new BigDecimal("104.90"));
        order.setRegion("US");
        order.setPriority(OrderPriority.PRIORITY);
        order.setReceivedAt(now);
        order.setProcessedAt(now);
        order.setStage("COMPLETED");

        assertThat(order.getOrderId()).isEqualTo("ORD-002");
        assertThat(order.getCustomerId()).isEqualTo("US-CUST-2");
        assertThat(order.getProductName()).isEqualTo("Gadget");
        assertThat(order.getUnitPrice()).isEqualByComparingTo("49.95");
        assertThat(order.getQuantity()).isEqualTo(10);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("499.50");
        assertThat(order.getVatAmount()).isEqualByComparingTo("104.90");
        assertThat(order.getRegion()).isEqualTo("US");
        assertThat(order.getPriority()).isEqualTo(OrderPriority.PRIORITY);
        assertThat(order.getReceivedAt()).isEqualTo(now);
        assertThat(order.getProcessedAt()).isEqualTo(now);
        assertThat(order.getStage()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("toString() contains key fields for log readability")
    void toStringContainsKeyFields() {
        Order order = new Order("ORD-003", "AP-CUST-3", "Sensor", new BigDecimal("15.00"), 5);
        order.setTotalAmount(new BigDecimal("75.00"));
        order.setPriority(OrderPriority.STANDARD);
        order.setRegion("APAC");
        order.setStage("CLASSIFIED");

        String str = order.toString();

        assertThat(str).contains("ORD-003");
        assertThat(str).contains("AP-CUST-3");
        assertThat(str).contains("Sensor");
        assertThat(str).contains("CLASSIFIED");
    }
}
