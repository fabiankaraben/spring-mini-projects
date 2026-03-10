package com.example.batchschedulers.batch.processor;

import com.example.batchschedulers.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PriceRefreshItemProcessor}.
 *
 * <p>These tests exercise the processor's domain logic in complete isolation:
 * no Spring context, no database, no Testcontainers. They run instantly and
 * verify that the price adjustment is applied correctly for a variety of inputs.
 *
 * <p>The {@code adjustmentFactor} field is injected via
 * {@link ReflectionTestUtils#setField} to avoid the need for a Spring context
 * while still testing behaviour that normally comes from {@code @Value}.
 */
@DisplayName("PriceRefreshItemProcessor – unit tests")
class PriceRefreshItemProcessorTest {

    /**
     * The processor under test.
     * Instantiated directly (no Spring context) to keep tests fast.
     */
    private PriceRefreshItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PriceRefreshItemProcessor();
        // Simulate the default @Value injection (1.02 = 2% increase)
        ReflectionTestUtils.setField(processor, "adjustmentFactor", new BigDecimal("1.02"));
    }

    // ── Core price adjustment tests ───────────────────────────────────────────

    @Test
    @DisplayName("process() should apply 2% increase to price")
    void process_appliesTwoPercentIncrease() throws Exception {
        // Arrange – product with a round price
        Product product = new Product("Test Laptop", "Electronics",
                new BigDecimal("1000.00"), 10);

        // Act
        Product result = processor.process(product);

        // Assert – 1000.00 * 1.02 = 1020.00
        assertThat(result).isNotNull();
        assertThat(result.getPrice()).isEqualByComparingTo("1020.00");
    }

    @Test
    @DisplayName("process() should never return null (all products pass through)")
    void process_neverReturnsNull() throws Exception {
        Product product = new Product("Any Product", "Books",
                new BigDecimal("50.00"), 5);
        assertThat(processor.process(product)).isNotNull();
    }

    @Test
    @DisplayName("process() should update lastPriceUpdate timestamp")
    void process_updatesLastPriceUpdateTimestamp() throws Exception {
        // Arrange – product with no previous update timestamp
        Product product = new Product("Monitor", "Electronics",
                new BigDecimal("400.00"), 20);
        assertThat(product.getLastPriceUpdate()).isNull();

        // Act
        processor.process(product);

        // Assert – the domain method applyPriceAdjustment sets lastPriceUpdate
        assertThat(product.getLastPriceUpdate()).isNotNull();
    }

    @Test
    @DisplayName("process() with 5% discount factor should reduce price")
    void process_withDiscountFactor_reducesPrice() throws Exception {
        // Arrange – override with a discount factor
        ReflectionTestUtils.setField(processor, "adjustmentFactor", new BigDecimal("0.95"));
        Product product = new Product("Book", "Books",
                new BigDecimal("40.00"), 30);

        // Act
        Product result = processor.process(product);

        // Assert – 40.00 * 0.95 = 38.00
        assertThat(result.getPrice()).isEqualByComparingTo("38.00");
    }

    @Test
    @DisplayName("process() with factor 1.00 should not change price")
    void process_withNoOpFactor_priceUnchanged() throws Exception {
        ReflectionTestUtils.setField(processor, "adjustmentFactor", new BigDecimal("1.00"));
        Product product = new Product("Hoodie", "Clothing",
                new BigDecimal("59.99"), 15);

        Product result = processor.process(product);

        assertThat(result.getPrice()).isEqualByComparingTo("59.99");
    }

    @Test
    @DisplayName("process() should round price to 2 decimal places")
    void process_roundsPriceToTwoDecimalPlaces() throws Exception {
        // Arrange – price that produces an irrational result
        // 33.33 * 1.02 = 33.9966 → rounds to 34.00
        ReflectionTestUtils.setField(processor, "adjustmentFactor", new BigDecimal("1.02"));
        Product product = new Product("Cable", "Electronics",
                new BigDecimal("33.33"), 100);

        Product result = processor.process(product);

        // 33.33 * 1.02 = 33.9966 → HALF_UP rounds to 34.00
        assertThat(result.getPrice().scale()).isEqualTo(2);
        assertThat(result.getPrice()).isEqualByComparingTo("34.00");
    }

    @Test
    @DisplayName("process() should return the same product instance (mutates in-place)")
    void process_returnsSameProductInstance() throws Exception {
        Product product = new Product("Keyboard", "Electronics",
                new BigDecimal("89.99"), 15);

        Product result = processor.process(product);

        // The processor modifies and returns the same entity (not a copy)
        assertThat(result).isSameAs(product);
    }
}
