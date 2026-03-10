package com.example.reactiver2dbc.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain entity.
 *
 * <p>These tests verify pure domain logic — constructor behaviour, field defaults,
 * and getter/setter contracts. No Spring context, no database, no mocks.
 *
 * <p>Why test the domain entity?
 * <ul>
 *   <li>Ensure the convenience constructor maps all parameters to the correct fields.</li>
 *   <li>Catch accidental field re-ordering bugs in the constructor signature early.</li>
 *   <li>Document the expected default values (e.g., {@code id} starts as {@code null}).</li>
 * </ul>
 *
 * <p>These tests run in milliseconds because they do not start any containers or
 * Spring contexts.
 */
@DisplayName("Product domain unit tests")
class ProductDomainTest {

    // ── Constructor tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("convenience constructor sets all fields correctly")
    void constructor_setsAllFieldsCorrectly() {
        // Given / When: create a product using the convenience constructor
        Product product = new Product(
                "Wireless Keyboard",
                "Compact TKL with Cherry MX switches",
                new BigDecimal("129.99"),
                "electronics",
                45,
                true
        );

        // Then: every field should match the constructor argument
        assertThat(product.getName()).isEqualTo("Wireless Keyboard");
        assertThat(product.getDescription()).isEqualTo("Compact TKL with Cherry MX switches");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("129.99"));
        assertThat(product.getCategory()).isEqualTo("electronics");
        assertThat(product.getStockQuantity()).isEqualTo(45);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    @DisplayName("id is null before persistence")
    void id_isNullBeforePersistence() {
        // The id field must be null so Spring Data R2DBC issues an INSERT (not UPDATE)
        Product product = new Product("Name", "Desc", BigDecimal.ONE, "cat", 1, true);
        assertThat(product.getId()).isNull();
    }

    @Test
    @DisplayName("audit timestamps are null before Spring Data populates them")
    void auditTimestamps_areNullBeforePersistence() {
        // @CreatedDate and @LastModifiedDate are set by Spring Data during save();
        // they should be null in the domain object before it is persisted.
        Product product = new Product("Name", "Desc", BigDecimal.ONE, "cat", 1, true);
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("no-arg constructor creates an instance with default primitive values")
    void noArgConstructor_createsInstanceWithDefaults() {
        // Required by Spring Data R2DBC for result-set mapping (reflection)
        Product product = new Product();
        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isNull();
        // int fields default to 0 (Java primitive default)
        assertThat(product.getStockQuantity()).isZero();
        // boolean fields default to false (Java primitive default)
        assertThat(product.isActive()).isFalse();
    }

    // ── Setter / getter round-trip tests ──────────────────────────────────────────

    @Test
    @DisplayName("setters update the corresponding fields")
    void setters_updateFields() {
        Product product = new Product();

        // When: set every field via setters
        product.setId(42L);
        product.setName("Updated Name");
        product.setDescription("Updated description");
        product.setPrice(new BigDecimal("99.50"));
        product.setCategory("furniture");
        product.setStockQuantity(10);
        product.setActive(true);

        // Then: getters return the newly set values
        assertThat(product.getId()).isEqualTo(42L);
        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getDescription()).isEqualTo("Updated description");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("99.50"));
        assertThat(product.getCategory()).isEqualTo("furniture");
        assertThat(product.getStockQuantity()).isEqualTo(10);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    @DisplayName("inactive product has active flag set to false")
    void inactiveProduct_hasFalseActiveFlag() {
        Product product = new Product("Hidden Item", "Not shown", BigDecimal.TEN, "misc", 0, false);
        assertThat(product.isActive()).isFalse();
    }

    @Test
    @DisplayName("product with zero stock is valid domain object")
    void zeroStockProduct_isValid() {
        // Zero stock is valid — it means the product is out of stock but still exists
        Product product = new Product("Out of Stock", "No units", BigDecimal.ONE, "cat", 0, true);
        assertThat(product.getStockQuantity()).isZero();
    }
}
