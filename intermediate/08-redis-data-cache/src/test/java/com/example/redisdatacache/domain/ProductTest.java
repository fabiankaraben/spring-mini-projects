package com.example.redisdatacache.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain model.
 *
 * <p>These tests verify the behaviour of the plain Java class in isolation —
 * no Spring context, no Redis, no network. They run extremely fast (milliseconds)
 * and serve as the innermost layer of the test pyramid.
 */
@DisplayName("Product domain model")
class ProductTest {

    // ── Constructor tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("no-arg constructor creates a product with all null fields")
    void noArgConstructor_createsProductWithNullFields() {
        // When: a product is created with the no-arg constructor
        Product product = new Product();

        // Then: all fields should be null (no defaults assigned in the no-arg constructor)
        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isNull();
        assertThat(product.getDescription()).isNull();
        assertThat(product.getPrice()).isNull();
        assertThat(product.getCategory()).isNull();
        assertThat(product.getLastModified()).isNull();
    }

    @Test
    @DisplayName("all-args constructor populates all fields and sets lastModified")
    void allArgsConstructor_populatesAllFields() {
        // Given: a fixed set of product data
        Long id = 42L;
        String name = "Test Product";
        String description = "A test description";
        BigDecimal price = new BigDecimal("99.99");
        String category = "test-category";

        // Record the time just before creation to bound the lastModified check
        Instant before = Instant.now();

        // When: a product is created with the all-args constructor
        Product product = new Product(id, name, description, price, category);

        Instant after = Instant.now();

        // Then: all fields match the provided values
        assertThat(product.getId()).isEqualTo(id);
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getDescription()).isEqualTo(description);
        assertThat(product.getPrice()).isEqualByComparingTo(price);
        assertThat(product.getCategory()).isEqualTo(category);

        // lastModified should be set automatically to the current time
        assertThat(product.getLastModified())
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // ── Getter / setter tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("setters update the corresponding fields")
    void setters_updateFields() {
        // Given: an empty product
        Product product = new Product();

        // When: fields are set individually
        product.setId(1L);
        product.setName("Updated Name");
        product.setDescription("Updated Description");
        product.setPrice(new BigDecimal("149.95"));
        product.setCategory("updated-category");
        Instant now = Instant.now();
        product.setLastModified(now);

        // Then: getters return the expected values
        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getDescription()).isEqualTo("Updated Description");
        assertThat(product.getPrice()).isEqualByComparingTo("149.95");
        assertThat(product.getCategory()).isEqualTo("updated-category");
        assertThat(product.getLastModified()).isEqualTo(now);
    }

    // ── toString tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes id, name, price, and category")
    void toString_includesKeyFields() {
        // Given: a fully populated product
        Product product = new Product(7L, "Sample", "desc", new BigDecimal("29.99"), "tools");

        // When
        String result = product.toString();

        // Then: the key identifiers are present in the string representation
        assertThat(result).contains("7", "Sample", "29.99", "tools");
    }

    // ── Serialisability ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Product is Serializable (required for Redis Java serialisation)")
    void product_implementsSerializable() {
        // This test documents that Product is Serializable so Redis can cache it.
        // If someone removes the Serializable interface the test will fail at compile
        // time (Product would no longer be assignable to java.io.Serializable).
        Product product = new Product(1L, "Laptop", "desc", BigDecimal.TEN, "electronics");
        assertThat(product).isInstanceOf(java.io.Serializable.class);
    }
}
