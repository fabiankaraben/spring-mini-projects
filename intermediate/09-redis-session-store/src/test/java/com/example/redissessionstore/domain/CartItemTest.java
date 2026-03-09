package com.example.redissessionstore.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link CartItem} domain model.
 *
 * <p>These tests verify the domain logic in complete isolation — no Spring context,
 * no mocks, no external dependencies. They run in milliseconds.
 */
@DisplayName("CartItem unit tests")
class CartItemTest {

    // ── Constructor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All-args constructor sets all fields correctly")
    void constructor_setsAllFields() {
        // When: a CartItem is created with all args
        CartItem item = new CartItem("p1", "Laptop", new BigDecimal("999.99"), 2);

        // Then: all fields reflect the constructor arguments
        assertThat(item.getProductId()).isEqualTo("p1");
        assertThat(item.getProductName()).isEqualTo("Laptop");
        assertThat(item.getPrice()).isEqualByComparingTo("999.99");
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("No-arg constructor leaves all fields as null/zero")
    void noArgConstructor_leavesFieldsAsDefaults() {
        // When: a CartItem is created with the no-arg constructor (used by Jackson)
        CartItem item = new CartItem();

        // Then: reference fields are null and int defaults to 0
        assertThat(item.getProductId()).isNull();
        assertThat(item.getProductName()).isNull();
        assertThat(item.getPrice()).isNull();
        assertThat(item.getQuantity()).isZero();
    }

    // ── lineTotal ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lineTotal returns price * quantity for a single unit")
    void lineTotal_singleUnit() {
        // Given: an item priced at 10.00 with quantity 1
        CartItem item = new CartItem("p1", "Book", new BigDecimal("10.00"), 1);

        // When
        BigDecimal total = item.lineTotal();

        // Then: total equals the unit price
        assertThat(total).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("lineTotal returns price * quantity for multiple units")
    void lineTotal_multipleUnits() {
        // Given: an item priced at 5.50 with quantity 4
        CartItem item = new CartItem("p2", "Pen", new BigDecimal("5.50"), 4);

        // When
        BigDecimal total = item.lineTotal();

        // Then: 5.50 * 4 = 22.00
        assertThat(total).isEqualByComparingTo("22.00");
    }

    @Test
    @DisplayName("lineTotal handles decimal prices correctly")
    void lineTotal_decimalPrice() {
        // Given: price with several decimal places
        CartItem item = new CartItem("p3", "Widget", new BigDecimal("3.33"), 3);

        // When
        BigDecimal total = item.lineTotal();

        // Then: 3.33 * 3 = 9.99
        assertThat(total).isEqualByComparingTo("9.99");
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Setters mutate the fields and getters return the updated values")
    void settersAndGetters_workCorrectly() {
        // Given: a blank CartItem
        CartItem item = new CartItem();

        // When: values are set via setters
        item.setProductId("p99");
        item.setProductName("Updated Product");
        item.setPrice(new BigDecimal("49.95"));
        item.setQuantity(7);

        // Then: getters return the set values
        assertThat(item.getProductId()).isEqualTo("p99");
        assertThat(item.getProductName()).isEqualTo("Updated Product");
        assertThat(item.getPrice()).isEqualByComparingTo("49.95");
        assertThat(item.getQuantity()).isEqualTo(7);
    }

    // ── toString ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString contains key fields for debugging")
    void toString_containsKeyFields() {
        // Given
        CartItem item = new CartItem("p1", "Laptop", new BigDecimal("999.99"), 2);

        // When
        String str = item.toString();

        // Then: the string representation includes productId, productName, price, quantity
        assertThat(str).contains("p1");
        assertThat(str).contains("Laptop");
        assertThat(str).contains("999.99");
        assertThat(str).contains("2");
    }
}
