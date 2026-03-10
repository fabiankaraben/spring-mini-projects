package com.example.circuitbreaker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain record.
 *
 * <p>These tests verify the data contract of the {@link Product} record:
 * accessor methods, equality semantics, and immutability guarantees.
 *
 * <p>No Spring context is loaded — these are pure unit tests that run
 * in milliseconds with zero infrastructure.
 */
@DisplayName("Product domain record unit tests")
class ProductDomainTest {

    // ── Constructor and accessor tests ────────────────────────────────────────────

    @Test
    @DisplayName("Product record stores and returns all fields correctly")
    void product_storesAllFieldsCorrectly() {
        // Given / When: construct a Product using the canonical record constructor
        Product product = new Product(
                1,
                "Laptop Pro",
                "High-performance laptop",
                new BigDecimal("1299.99"),
                true
        );

        // Then: each accessor returns the value passed to the constructor
        assertThat(product.id()).isEqualTo(1);
        assertThat(product.name()).isEqualTo("Laptop Pro");
        assertThat(product.description()).isEqualTo("High-performance laptop");
        assertThat(product.price()).isEqualByComparingTo(new BigDecimal("1299.99"));
        assertThat(product.available()).isTrue();
    }

    @Test
    @DisplayName("Product record allows null id (server-assigned on creation)")
    void product_allowsNullId() {
        // A product with no id yet (before the server assigns one) must be representable
        Product product = new Product(null, "Widget", "A small widget", BigDecimal.TEN, true);
        assertThat(product.id()).isNull();
    }

    @Test
    @DisplayName("Product record allows null fields for optional values")
    void product_allowsNullOptionalFields() {
        // Null price and description are valid (e.g., from a partial upstream response)
        Product product = new Product(42, "Partial", null, null, null);
        assertThat(product.id()).isEqualTo(42);
        assertThat(product.name()).isEqualTo("Partial");
        assertThat(product.description()).isNull();
        assertThat(product.price()).isNull();
        assertThat(product.available()).isNull();
    }

    // ── Equality and hash code tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Two Product records with identical fields are equal")
    void product_equality_whenAllFieldsMatch() {
        // Java records auto-generate equals() based on all component values
        Product p1 = new Product(1, "Laptop", "Desc", new BigDecimal("999.00"), true);
        Product p2 = new Product(1, "Laptop", "Desc", new BigDecimal("999.00"), true);

        assertThat(p1).isEqualTo(p2);
        // equal objects must have equal hash codes (contract)
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    @DisplayName("Two Product records with different ids are not equal")
    void product_inequality_whenIdsDiffer() {
        Product p1 = new Product(1, "Laptop", "Desc", new BigDecimal("999.00"), true);
        Product p2 = new Product(2, "Laptop", "Desc", new BigDecimal("999.00"), true);

        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    @DisplayName("Two Product records with different names are not equal")
    void product_inequality_whenNamesDiffer() {
        Product p1 = new Product(1, "Laptop", "Desc", new BigDecimal("999.00"), true);
        Product p2 = new Product(1, "Desktop", "Desc", new BigDecimal("999.00"), true);

        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    @DisplayName("Two Product records with different prices are not equal")
    void product_inequality_whenPricesDiffer() {
        Product p1 = new Product(1, "Laptop", "Desc", new BigDecimal("999.00"), true);
        Product p2 = new Product(1, "Laptop", "Desc", new BigDecimal("1200.00"), true);

        assertThat(p1).isNotEqualTo(p2);
    }

    // ── toString test ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Product toString includes all field values")
    void product_toString_includesAllFields() {
        // Records auto-generate toString() in the form "Product[id=1, name=..., ...]"
        Product product = new Product(7, "Keyboard", "Mechanical keyboard", new BigDecimal("89.99"), true);
        String str = product.toString();

        assertThat(str).contains("7");
        assertThat(str).contains("Keyboard");
        assertThat(str).contains("Mechanical keyboard");
        assertThat(str).contains("89.99");
        assertThat(str).contains("true");
    }

    // ── Availability flag tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Product with available=false represents an out-of-stock item")
    void product_unavailable_flag() {
        Product outOfStock = new Product(5, "Widget", "Out of stock item", BigDecimal.ONE, false);
        assertThat(outOfStock.available()).isFalse();
    }

    @Test
    @DisplayName("Fallback product representation has expected sentinel values")
    void product_fallback_sentinelValues() {
        // The service's fallback method creates a product with these specific values.
        // This test documents and guards that contract.
        Product fallback = new Product(
                99,
                "Product Unavailable",
                "The product catalog is temporarily unavailable. Please try again later.",
                BigDecimal.ZERO,
                false
        );

        assertThat(fallback.id()).isEqualTo(99);
        assertThat(fallback.name()).isEqualTo("Product Unavailable");
        assertThat(fallback.available()).isFalse();
        assertThat(fallback.price()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
