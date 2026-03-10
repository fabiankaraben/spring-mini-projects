package com.example.batchschedulers.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain model.
 *
 * <p>Exercises the domain methods {@link Product#applyPriceAdjustment(BigDecimal)}
 * and {@link Product#auditInventory()} in complete isolation (no Spring context,
 * no database). These tests verify that the business rules encoded in the entity
 * are correct independently of the batch infrastructure.
 */
@DisplayName("Product domain model – unit tests")
class ProductTest {

    // ── applyPriceAdjustment tests ────────────────────────────────────────────

    @Test
    @DisplayName("applyPriceAdjustment() should increase price by given factor")
    void applyPriceAdjustment_increasesPrice() {
        Product product = new Product("Laptop", "Electronics",
                new BigDecimal("1000.00"), 20);

        product.applyPriceAdjustment(new BigDecimal("1.10"));

        // 1000.00 * 1.10 = 1100.00
        assertThat(product.getPrice()).isEqualByComparingTo("1100.00");
    }

    @Test
    @DisplayName("applyPriceAdjustment() should decrease price when factor < 1")
    void applyPriceAdjustment_decreasesPrice() {
        Product product = new Product("Book", "Books",
                new BigDecimal("50.00"), 30);

        product.applyPriceAdjustment(new BigDecimal("0.90"));

        // 50.00 * 0.90 = 45.00
        assertThat(product.getPrice()).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("applyPriceAdjustment() should set lastPriceUpdate to non-null")
    void applyPriceAdjustment_setsLastPriceUpdate() {
        Product product = new Product("Monitor", "Electronics",
                new BigDecimal("400.00"), 10);
        assertThat(product.getLastPriceUpdate()).isNull();

        product.applyPriceAdjustment(new BigDecimal("1.02"));

        assertThat(product.getLastPriceUpdate()).isNotNull();
    }

    @Test
    @DisplayName("applyPriceAdjustment() should round result to 2 decimal places")
    void applyPriceAdjustment_roundsToTwoDecimalPlaces() {
        // 99.99 * 1.03 = 102.9897 → rounds to 102.99
        Product product = new Product("Headset", "Electronics",
                new BigDecimal("99.99"), 5);

        product.applyPriceAdjustment(new BigDecimal("1.03"));

        assertThat(product.getPrice().scale()).isEqualTo(2);
        assertThat(product.getPrice()).isEqualByComparingTo("102.99");
    }

    @Test
    @DisplayName("applyPriceAdjustment() with factor 1.00 should leave price unchanged")
    void applyPriceAdjustment_withNoOpFactor_priceUnchanged() {
        Product product = new Product("T-Shirt", "Clothing",
                new BigDecimal("24.99"), 40);

        product.applyPriceAdjustment(BigDecimal.ONE);

        assertThat(product.getPrice()).isEqualByComparingTo("24.99");
    }

    // ── auditInventory tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("auditInventory() should set lowStock=true when qty < threshold")
    void auditInventory_setsLowStock_whenBelowThreshold() {
        Product product = new Product("Webcam", "Electronics",
                new BigDecimal("79.99"), 5);

        product.auditInventory();

        assertThat(product.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("auditInventory() should set lowStock=false when qty == threshold")
    void auditInventory_clearsLowStock_whenAtThreshold() {
        Product product = new Product("Keyboard", "Electronics",
                new BigDecimal("89.99"), Product.LOW_STOCK_THRESHOLD);

        product.auditInventory();

        assertThat(product.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("auditInventory() should set lowStock=false when qty > threshold")
    void auditInventory_clearsLowStock_whenAboveThreshold() {
        Product product = new Product("SSD", "Electronics",
                new BigDecimal("109.99"), 100);

        product.auditInventory();

        assertThat(product.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("auditInventory() should clear a previously set lowStock flag")
    void auditInventory_clearsExistingFlag_whenStockRestored() {
        Product product = new Product("USB Hub", "Electronics",
                new BigDecimal("49.99"), 30);
        // Simulate a previously set flag
        product.setLowStock(true);

        product.auditInventory();

        assertThat(product.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("auditInventory() should always update lastAudited timestamp")
    void auditInventory_updatesLastAuditedTimestamp() {
        Product product = new Product("Cap", "Clothing",
                new BigDecimal("19.99"), 50);
        assertThat(product.getLastAudited()).isNull();

        product.auditInventory();

        assertThat(product.getLastAudited()).isNotNull();
    }

    @Test
    @DisplayName("auditInventory() threshold boundary: qty=9 should be flagged")
    void auditInventory_threshold_qtyNineIsFlagged() {
        // 9 < 10 (threshold) → low stock
        Product product = new Product("Organiser", "Clothing",
                new BigDecimal("14.99"), 9);

        product.auditInventory();

        assertThat(product.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("auditInventory() threshold boundary: qty=10 should NOT be flagged")
    void auditInventory_threshold_qtyTenIsNotFlagged() {
        // 10 == threshold → NOT low stock
        Product product = new Product("Backpack", "Clothing",
                new BigDecimal("79.99"), 10);

        product.auditInventory();

        assertThat(product.isLowStock()).isFalse();
    }
}
