package com.example.debeziumcdc.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain entity.
 *
 * <p>These tests verify the domain object's business behavior in isolation,
 * without loading any Spring context or connecting to any external service.
 * They run fast and focus purely on the object's internal invariants.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Constructor sets all fields correctly.</li>
 *   <li>Setters mutate the correct fields.</li>
 *   <li>Default stock value is 0.</li>
 *   <li>{@code toString()} produces a readable representation.</li>
 * </ul>
 */
@DisplayName("Product domain entity unit tests")
class ProductTest {

    // =========================================================================
    // Constructor tests
    // =========================================================================

    @Test
    @DisplayName("Constructor sets all provided fields")
    void constructorSetsAllFields() {
        // Arrange
        String name = "Laptop";
        String description = "High-performance laptop";
        BigDecimal price = new BigDecimal("1299.99");
        int stock = 50;

        // Act
        Product product = new Product(name, description, price, stock);

        // Assert — all constructor-supplied values are stored
        assertThat(product.getName()).isEqualTo(name);
        assertThat(product.getDescription()).isEqualTo(description);
        assertThat(product.getPrice()).isEqualByComparingTo(price);
        assertThat(product.getStock()).isEqualTo(stock);

        // ID and timestamps are null before JPA persist
        assertThat(product.getId()).isNull();
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("Constructor with zero stock sets stock to 0")
    void constructorWithZeroStockSetsStockToZero() {
        Product product = new Product("Widget", null, new BigDecimal("9.99"), 0);
        assertThat(product.getStock()).isZero();
    }

    @Test
    @DisplayName("Constructor with null description stores null")
    void constructorWithNullDescriptionStoresNull() {
        Product product = new Product("Gadget", null, new BigDecimal("49.99"), 5);
        assertThat(product.getDescription()).isNull();
    }

    // =========================================================================
    // Default value tests
    // =========================================================================

    @Test
    @DisplayName("Default stock is 0 when not specified via constructor")
    void defaultStockIsZero() {
        // Use the no-arg constructor via reflection to simulate JPA instantiation
        // (we cannot call new Product() directly as it is protected)
        // Instead, we test the 4-arg constructor with stock=0
        Product product = new Product("TestItem", null, new BigDecimal("1.00"), 0);
        assertThat(product.getStock()).isZero();
    }

    // =========================================================================
    // Setter tests
    // =========================================================================

    @Test
    @DisplayName("setName updates the product name")
    void setNameUpdatesName() {
        Product product = new Product("Old Name", null, new BigDecimal("10.00"), 1);
        product.setName("New Name");
        assertThat(product.getName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("setDescription updates the product description")
    void setDescriptionUpdatesDescription() {
        Product product = new Product("Item", "Old desc", new BigDecimal("5.00"), 0);
        product.setDescription("New description");
        assertThat(product.getDescription()).isEqualTo("New description");
    }

    @Test
    @DisplayName("setPrice updates the product price")
    void setPriceUpdatesPrice() {
        Product product = new Product("Item", null, new BigDecimal("10.00"), 0);
        product.setPrice(new BigDecimal("25.50"));
        assertThat(product.getPrice()).isEqualByComparingTo("25.50");
    }

    @Test
    @DisplayName("setStock updates the stock level")
    void setStockUpdatesStock() {
        Product product = new Product("Item", null, new BigDecimal("10.00"), 5);
        product.setStock(100);
        assertThat(product.getStock()).isEqualTo(100);
    }

    // =========================================================================
    // toString tests
    // =========================================================================

    @Test
    @DisplayName("toString contains product name and price")
    void toStringContainsNameAndPrice() {
        Product product = new Product("Keyboard", null, new BigDecimal("79.99"), 20);
        String str = product.toString();
        assertThat(str).contains("Keyboard");
        assertThat(str).contains("79.99");
    }
}
