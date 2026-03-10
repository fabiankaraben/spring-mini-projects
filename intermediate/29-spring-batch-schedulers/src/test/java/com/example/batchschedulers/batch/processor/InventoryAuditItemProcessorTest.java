package com.example.batchschedulers.batch.processor;

import com.example.batchschedulers.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InventoryAuditItemProcessor}.
 *
 * <p>Tests are pure unit tests – no Spring context, no database. They directly
 * instantiate {@link InventoryAuditItemProcessor} and verify the low-stock
 * flagging logic and {@code lastAudited} timestamp update behaviour.
 *
 * <p>The low-stock threshold is defined in {@link Product#LOW_STOCK_THRESHOLD}
 * (= 10 units). Products with {@code stockQuantity < 10} should be flagged.
 */
@DisplayName("InventoryAuditItemProcessor – unit tests")
class InventoryAuditItemProcessorTest {

    /** Processor under test, instantiated without a Spring context. */
    private InventoryAuditItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InventoryAuditItemProcessor();
    }

    // ── Low-stock flagging tests ───────────────────────────────────────────────

    @Test
    @DisplayName("process() should flag product as low-stock when qty < threshold")
    void process_flagsLowStock_whenBelowThreshold() throws Exception {
        // Arrange – stock quantity below the threshold (10)
        Product product = new Product("Webcam", "Electronics",
                new BigDecimal("79.99"), 3);

        // Act
        Product result = processor.process(product);

        // Assert – low-stock flag must be true for qty=3 < 10
        assertThat(result.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("process() should NOT flag product as low-stock when qty == threshold")
    void process_doesNotFlagLowStock_whenAtThreshold() throws Exception {
        // Arrange – stock quantity exactly at the threshold
        Product product = new Product("Mouse", "Electronics",
                new BigDecimal("29.99"), Product.LOW_STOCK_THRESHOLD);

        // Act
        Product result = processor.process(product);

        // Assert – qty == threshold (10) is NOT below threshold, so lowStock = false
        assertThat(result.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("process() should NOT flag product as low-stock when qty > threshold")
    void process_doesNotFlagLowStock_whenAboveThreshold() throws Exception {
        // Arrange – plenty of stock
        Product product = new Product("Laptop", "Electronics",
                new BigDecimal("1299.99"), 50);

        // Act
        Product result = processor.process(product);

        // Assert
        assertThat(result.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("process() should clear low-stock flag when qty is restored above threshold")
    void process_clearsLowStockFlag_whenStockRestored() throws Exception {
        // Arrange – product that was previously flagged as low-stock
        Product product = new Product("Monitor", "Electronics",
                new BigDecimal("449.99"), 20);
        product.setLowStock(true); // simulate previously flagged state

        // Act – after restock, qty=20 is above threshold
        Product result = processor.process(product);

        // Assert – flag should be cleared
        assertThat(result.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("process() should update lastAudited timestamp")
    void process_updatesLastAuditedTimestamp() throws Exception {
        // Arrange – product with no prior audit
        Product product = new Product("Keyboard", "Electronics",
                new BigDecimal("89.99"), 15);
        assertThat(product.getLastAudited()).isNull();

        // Act
        processor.process(product);

        // Assert – lastAudited should now be set
        assertThat(product.getLastAudited()).isNotNull();
    }

    @Test
    @DisplayName("process() should never return null (all products pass through)")
    void process_neverReturnsNull() throws Exception {
        Product product = new Product("Book", "Books",
                new BigDecimal("39.99"), 50);
        assertThat(processor.process(product)).isNotNull();
    }

    @Test
    @DisplayName("process() should return same product instance (mutates in-place)")
    void process_returnsSameProductInstance() throws Exception {
        Product product = new Product("SSD", "Electronics",
                new BigDecimal("109.99"), 8);
        Product result = processor.process(product);
        assertThat(result).isSameAs(product);
    }

    @Test
    @DisplayName("process() should flag product as low-stock when qty = 0")
    void process_flagsLowStock_whenQtyIsZero() throws Exception {
        Product product = new Product("Out-of-stock item", "Clothing",
                new BigDecimal("24.99"), 0);

        Product result = processor.process(product);

        assertThat(result.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("process() should flag product as low-stock when qty = threshold - 1")
    void process_flagsLowStock_whenQtyIsJustBelowThreshold() throws Exception {
        // qty = 9 is the highest value that should trigger low-stock
        Product product = new Product("Headset", "Electronics",
                new BigDecimal("199.99"), Product.LOW_STOCK_THRESHOLD - 1);

        Product result = processor.process(product);

        assertThat(result.isLowStock()).isTrue();
    }
}
