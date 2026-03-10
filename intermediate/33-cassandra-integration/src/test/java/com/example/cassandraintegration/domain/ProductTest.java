package com.example.cassandraintegration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Product} domain entity.
 *
 * <p>These tests verify that the entity's constructors, getters, setters,
 * and {@code toString} method behave correctly — completely in-memory,
 * without any Spring context or database connection.</p>
 */
@DisplayName("Product domain unit tests")
class ProductTest {

    /** A fixed UUID used across tests for predictability. */
    private static final UUID FIXED_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // -------------------------------------------------------------------------
    // Constructor tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("no-arg constructor creates an empty Product")
    void noArgConstructor_createsEmptyProduct() {
        Product product = new Product();

        // All fields should be null / default
        assertThat(product.getCategory()).isNull();
        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isNull();
        assertThat(product.getDescription()).isNull();
        assertThat(product.getPrice()).isNull();
        assertThat(product.getStock()).isZero();
    }

    @Test
    @DisplayName("full constructor sets all fields correctly")
    void fullConstructor_setsAllFields() {
        Product product = new Product(
                "Electronics",
                FIXED_ID,
                "Laptop Pro",
                "A high-end laptop",
                new BigDecimal("1299.99"),
                25
        );

        assertThat(product.getCategory()).isEqualTo("Electronics");
        assertThat(product.getId()).isEqualTo(FIXED_ID);
        assertThat(product.getName()).isEqualTo("Laptop Pro");
        assertThat(product.getDescription()).isEqualTo("A high-end laptop");
        assertThat(product.getPrice()).isEqualByComparingTo("1299.99");
        assertThat(product.getStock()).isEqualTo(25);
    }

    @Test
    @DisplayName("full constructor accepts null description")
    void fullConstructor_acceptsNullDescription() {
        Product product = new Product(
                "Books",
                FIXED_ID,
                "Spring Boot in Action",
                null,          // description is optional
                new BigDecimal("39.99"),
                100
        );

        assertThat(product.getDescription()).isNull();
        assertThat(product.getName()).isEqualTo("Spring Boot in Action");
    }

    // -------------------------------------------------------------------------
    // Setter / getter round-trip tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("setCategory and getCategory work correctly")
    void setCategory_updatesCategory() {
        Product product = new Product();
        product.setCategory("Clothing");

        assertThat(product.getCategory()).isEqualTo("Clothing");
    }

    @Test
    @DisplayName("setId and getId work correctly")
    void setId_updatesId() {
        Product product = new Product();
        UUID newId = UUID.randomUUID();
        product.setId(newId);

        assertThat(product.getId()).isEqualTo(newId);
    }

    @Test
    @DisplayName("setName and getName work correctly")
    void setName_updatesName() {
        Product product = new Product();
        product.setName("Wireless Mouse");

        assertThat(product.getName()).isEqualTo("Wireless Mouse");
    }

    @Test
    @DisplayName("setDescription and getDescription work correctly")
    void setDescription_updatesDescription() {
        Product product = new Product();
        product.setDescription("Ergonomic wireless mouse");

        assertThat(product.getDescription()).isEqualTo("Ergonomic wireless mouse");
    }

    @Test
    @DisplayName("setPrice and getPrice work correctly")
    void setPrice_updatesPrice() {
        Product product = new Product();
        product.setPrice(new BigDecimal("29.99"));

        assertThat(product.getPrice()).isEqualByComparingTo("29.99");
    }

    @Test
    @DisplayName("setStock and getStock work correctly")
    void setStock_updatesStock() {
        Product product = new Product();
        product.setStock(150);

        assertThat(product.getStock()).isEqualTo(150);
    }

    @Test
    @DisplayName("stock can be set to zero (out-of-stock)")
    void setStock_zeroIsValid() {
        Product product = new Product(
                "Electronics", FIXED_ID, "Discontinued Widget", null,
                new BigDecimal("9.99"), 0
        );

        assertThat(product.getStock()).isZero();
    }

    // -------------------------------------------------------------------------
    // toString test
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toString includes key fields")
    void toString_includesKeyFields() {
        Product product = new Product(
                "Electronics",
                FIXED_ID,
                "Laptop Pro",
                "A high-end laptop",
                new BigDecimal("1299.99"),
                25
        );

        String result = product.toString();

        assertThat(result).contains("Electronics");
        assertThat(result).contains(FIXED_ID.toString());
        assertThat(result).contains("Laptop Pro");
        assertThat(result).contains("1299.99");
    }
}
