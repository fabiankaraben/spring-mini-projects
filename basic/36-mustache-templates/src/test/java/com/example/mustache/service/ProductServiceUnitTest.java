package com.example.mustache.service;

import com.example.mustache.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductService}.
 *
 * <p>
 * These tests exercise the service <em>in isolation</em> — no Spring context is
 * started, no Mockito mocks are needed. {@link ProductService} has no external
 * dependencies (it holds an immutable in-memory list), so we instantiate it
 * directly with {@code new} and call its methods.
 * </p>
 *
 * <p>
 * Libraries used:
 * <ul>
 * <li>JUnit 5 ({@code @Test}, {@code @BeforeEach}, {@code @DisplayName})</li>
 * <li>AssertJ fluent assertions ({@code assertThat(...).isNotEmpty()},
 * etc.)</li>
 * </ul>
 * </p>
 */
class ProductServiceUnitTest {

    // The subject under test — created fresh before each test method
    private ProductService productService;

    /**
     * Runs before every test method.
     * Re-creating the service ensures test isolation even though the internal
     * list is immutable and shared state is not a concern here.
     */
    @BeforeEach
    void setUp() {
        productService = new ProductService();
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll() returns a non-empty list of products")
    void findAll_returnsNonEmptyList() {
        List<Product> products = productService.findAll();

        // AssertJ: the list should not be null or empty
        assertThat(products).isNotEmpty();
    }

    @Test
    @DisplayName("findAll() returns all 8 hardcoded products")
    void findAll_returnsAllEightProducts() {
        List<Product> products = productService.findAll();

        // We know the in-memory list has exactly 8 entries (see ProductService)
        assertThat(products).hasSize(8);
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById() returns the correct product for an existing ID")
    void findById_existingId_returnsProduct() {
        Optional<Product> result = productService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Wireless Headphones");
    }

    @Test
    @DisplayName("findById() returns empty Optional for a non-existing ID")
    void findById_nonExistingId_returnsEmpty() {
        Optional<Product> result = productService.findById(999L);

        // An ID that does not exist must yield an empty Optional — never null
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findInStock()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findInStock() returns only products where inStock is true")
    void findInStock_returnsOnlyInStockProducts() {
        List<Product> inStock = productService.findInStock();

        // Every returned product must have inStock == true
        assertThat(inStock).allMatch(Product::inStock);
    }

    @Test
    @DisplayName("findInStock() does not return out-of-stock products")
    void findInStock_doesNotContainOutOfStockProducts() {
        List<Product> inStock = productService.findInStock();

        // None of the returned products should have inStock == false
        assertThat(inStock).noneMatch(p -> !p.inStock());
    }

    // -------------------------------------------------------------------------
    // findByCategory()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByCategory() filters correctly for 'Electronics'")
    void findByCategory_electronics_returnsOnlyElectronics() {
        List<Product> electronics = productService.findByCategory("Electronics");

        assertThat(electronics).isNotEmpty();
        // Every returned product must belong to the requested category
        assertThat(electronics).allMatch(p -> p.category().equalsIgnoreCase("Electronics"));
    }

    @Test
    @DisplayName("findByCategory() is case-insensitive")
    void findByCategory_caseInsensitive() {
        List<Product> upper = productService.findByCategory("BOOKS");
        List<Product> lower = productService.findByCategory("books");

        // Both calls must return the same products regardless of letter case
        assertThat(upper).hasSameElementsAs(lower);
    }

    @Test
    @DisplayName("findByCategory() returns empty list for an unknown category")
    void findByCategory_unknownCategory_returnsEmptyList() {
        List<Product> result = productService.findByCategory("Unknown");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Product.formattedPrice()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Product.formattedPrice() returns a USD-formatted string")
    void product_formattedPrice_returnsFormattedString() {
        // Create a product with a known price and verify the helper method
        Product product = new Product(1L, "Test", "Test", 79.99, true);

        // formattedPrice() is used by Mustache templates via {{formattedPrice}}
        assertThat(product.formattedPrice()).isEqualTo("$79.99");
    }

    @Test
    @DisplayName("Product.formattedPrice() pads to two decimal places")
    void product_formattedPrice_padsTwoDecimalPlaces() {
        // A price with only one significant decimal should be padded
        Product product = new Product(2L, "Test", "Test", 5.5, true);

        assertThat(product.formattedPrice()).isEqualTo("$5.50");
    }
}
