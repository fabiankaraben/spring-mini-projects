package com.example.mongodbcrudapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain model.
 *
 * <p>These tests verify the basic behaviour of the domain object itself:
 * constructors, getters/setters, and toString. No Spring context or database
 * is required — this is a pure Java unit test that runs instantly.
 *
 * <p>Even though the domain model looks trivial, testing it guards against
 * accidental regressions (e.g. a getter returning the wrong field) and
 * documents the expected behaviour for future maintainers.
 */
@DisplayName("Product domain model unit tests")
class ProductTest {

    // ── Constructor tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("No-arg constructor creates a product with all null fields")
    void noArgConstructor_createsProductWithNullFields() {
        // When: a Product is created with the no-arg constructor
        Product product = new Product();

        // Then: all fields should be null (no defaults set by the constructor)
        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isNull();
        assertThat(product.getDescription()).isNull();
        assertThat(product.getPrice()).isNull();
        assertThat(product.getCategory()).isNull();
        assertThat(product.getStockQuantity()).isNull();
        // createdAt and updatedAt are only set by the convenience constructor
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Convenience constructor populates all provided fields")
    void convenienceConstructor_populatesAllFields() {
        // Given: values for all constructor parameters
        String name = "Laptop Pro";
        String description = "High-performance laptop";
        BigDecimal price = new BigDecimal("1299.99");
        String category = "electronics";
        Integer stock = 50;

        // When: a Product is created using the convenience constructor
        Instant before = Instant.now();
        Product product = new Product(name, description, price, category, stock);
        Instant after = Instant.now();

        // Then: all provided fields are set correctly
        assertThat(product.getId()).isNull(); // id is assigned by MongoDB on insert
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getDescription()).isEqualTo(description);
        assertThat(product.getPrice()).isEqualByComparingTo(price);
        assertThat(product.getCategory()).isEqualTo(category);
        assertThat(product.getStockQuantity()).isEqualTo(stock);

        // Timestamps should have been set to approximately "now"
        assertThat(product.getCreatedAt()).isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
        assertThat(product.getUpdatedAt()).isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // ── Setter tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Setters update all mutable fields correctly")
    void setters_updateFieldsCorrectly() {
        // Given: a product with initial values
        Product product = new Product("Old Name", "Old Desc", new BigDecimal("10.00"), "old-cat", 5);

        // When: all setters are called with new values
        product.setId("507f1f77bcf86cd799439011");
        product.setName("New Name");
        product.setDescription("New Desc");
        product.setPrice(new BigDecimal("99.99"));
        product.setCategory("new-cat");
        product.setStockQuantity(100);
        Instant newTime = Instant.now();
        product.setCreatedAt(newTime);
        product.setUpdatedAt(newTime);

        // Then: all getters reflect the updated values
        assertThat(product.getId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(product.getName()).isEqualTo("New Name");
        assertThat(product.getDescription()).isEqualTo("New Desc");
        assertThat(product.getPrice()).isEqualByComparingTo("99.99");
        assertThat(product.getCategory()).isEqualTo("new-cat");
        assertThat(product.getStockQuantity()).isEqualTo(100);
        assertThat(product.getCreatedAt()).isEqualTo(newTime);
        assertThat(product.getUpdatedAt()).isEqualTo(newTime);
    }

    // ── Price precision tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("BigDecimal price preserves exact decimal precision")
    void price_preservesDecimalPrecision() {
        // Given: a price that would lose precision with floating-point types
        BigDecimal exactPrice = new BigDecimal("19.99");
        Product product = new Product("Item", "desc", exactPrice, "test", 1);

        // When/Then: the price is retrieved without rounding errors
        // BigDecimal.equals() checks value AND scale; isEqualByComparingTo() checks value only
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(product.getPrice().toPlainString()).isEqualTo("19.99");
    }

    // ── toString tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes id, name, price, category, and stockQuantity")
    void toString_includesKeyFields() {
        // Given: a product with known values
        Product product = new Product("Keyboard", "Mechanical keyboard", new BigDecimal("89.99"), "peripherals", 30);
        product.setId("abc123");

        // When
        String result = product.toString();

        // Then: the string representation contains all key fields
        assertThat(result).contains("abc123");
        assertThat(result).contains("Keyboard");
        assertThat(result).contains("89.99");
        assertThat(result).contains("peripherals");
        assertThat(result).contains("30");
    }

    @Test
    @DisplayName("toString handles null id gracefully")
    void toString_handlesNullId() {
        // Given: a product whose id has not been set (not yet persisted)
        Product product = new Product("Widget", "A small widget", new BigDecimal("5.00"), "misc", 10);

        // When/Then: toString should not throw NullPointerException
        assertThat(product.toString()).contains("null");
    }
}
