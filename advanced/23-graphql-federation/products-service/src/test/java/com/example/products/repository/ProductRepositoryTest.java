package com.example.products.repository;

import com.example.products.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductRepository}.
 *
 * <p>These are pure unit tests — no Spring context is loaded, no network
 * calls are made. We instantiate the repository directly and exercise its
 * in-memory query logic.
 *
 * <p>Testing philosophy:
 * <ul>
 *   <li>Each test is isolated (a fresh repository is created in {@link #setUp()}).</li>
 *   <li>Assertions are written with AssertJ for readable failure messages.</li>
 *   <li>{@link DisplayName} annotations make the test report self-documenting.</li>
 * </ul>
 */
@DisplayName("ProductRepository unit tests")
class ProductRepositoryTest {

    /**
     * The system under test — a fresh in-memory repository with seeded data.
     * Re-created before each test to guarantee isolation.
     */
    private ProductRepository repository;

    @BeforeEach
    void setUp() {
        // Instantiate directly — no Spring context needed for a plain repository
        repository = new ProductRepository();
    }

    // =========================================================================
    // findAll()
    // =========================================================================

    @Test
    @DisplayName("findAll() returns all 8 seeded products")
    void findAll_returnsAllSeededProducts() {
        List<Product> products = repository.findAll();

        // The repository seeds exactly 8 products in its constructor
        assertThat(products)
                .as("Repository should contain all 8 seeded products")
                .hasSize(8);
    }

    @Test
    @DisplayName("findAll() returns products with required fields populated")
    void findAll_productsHaveRequiredFields() {
        List<Product> products = repository.findAll();

        // Every product must have a non-blank id, name, description, and category
        assertThat(products).allSatisfy(p -> {
            assertThat(p.id()).isNotBlank();
            assertThat(p.name()).isNotBlank();
            assertThat(p.description()).isNotBlank();
            assertThat(p.category()).isNotBlank();
            assertThat(p.price()).isNotNull().isPositive();
        });
    }

    // =========================================================================
    // findById()
    // =========================================================================

    @Test
    @DisplayName("findById() returns the correct product for a known ID")
    void findById_knownId_returnsProduct() {
        Optional<Product> result = repository.findById("prod-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("prod-1");
        assertThat(result.get().name()).isEqualTo("Wireless Keyboard");
    }

    @Test
    @DisplayName("findById() returns empty Optional for an unknown ID")
    void findById_unknownId_returnsEmpty() {
        Optional<Product> result = repository.findById("prod-999");

        assertThat(result)
                .as("Non-existent product ID should return empty Optional")
                .isEmpty();
    }

    @Test
    @DisplayName("findById() returns empty Optional for null-like blank ID")
    void findById_blankId_returnsEmpty() {
        // ConcurrentHashMap.get("") returns null → Optional.empty()
        Optional<Product> result = repository.findById("");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // findByCategory()
    // =========================================================================

    @Test
    @DisplayName("findByCategory() returns only Electronics products")
    void findByCategory_electronics_returnsCorrectProducts() {
        List<Product> electronics = repository.findByCategory("Electronics");

        // Seeded data has 5 electronics products: prod-1,2,5,6,7
        assertThat(electronics)
                .as("Should find 5 Electronics products")
                .hasSize(5);

        // All returned products must have the Electronics category
        assertThat(electronics).allMatch(p -> p.category().equalsIgnoreCase("Electronics"));
    }

    @Test
    @DisplayName("findByCategory() is case-insensitive")
    void findByCategory_caseInsensitive_returnsResults() {
        List<Product> lower = repository.findByCategory("electronics");
        List<Product> upper = repository.findByCategory("ELECTRONICS");
        List<Product> mixed = repository.findByCategory("Electronics");

        // All three calls should return the same number of products
        assertThat(lower).hasSameSizeAs(upper);
        assertThat(lower).hasSameSizeAs(mixed);
    }

    @Test
    @DisplayName("findByCategory() returns only Furniture products")
    void findByCategory_furniture_returnsCorrectProducts() {
        List<Product> furniture = repository.findByCategory("Furniture");

        // Seeded data has 3 furniture products: prod-3,4,8
        assertThat(furniture)
                .as("Should find 3 Furniture products")
                .hasSize(3);

        assertThat(furniture).allMatch(p -> p.category().equalsIgnoreCase("Furniture"));
    }

    @Test
    @DisplayName("findByCategory() returns empty list for unknown category")
    void findByCategory_unknownCategory_returnsEmptyList() {
        List<Product> result = repository.findByCategory("Clothing");

        assertThat(result)
                .as("Unknown category should return empty list")
                .isEmpty();
    }

    // =========================================================================
    // findInStock()
    // =========================================================================

    @Test
    @DisplayName("findInStock() returns only products with inStock == true")
    void findInStock_returnsOnlyInStockProducts() {
        List<Product> inStock = repository.findInStock();

        // All returned products must have inStock == true
        assertThat(inStock)
                .as("All returned products must be in stock")
                .allMatch(Product::inStock);
    }

    @Test
    @DisplayName("findInStock() excludes out-of-stock products")
    void findInStock_excludesOutOfStock() {
        List<Product> inStock = repository.findInStock();
        List<Product> all = repository.findAll();

        // The in-stock list must be smaller than the full list
        // (prod-4 and prod-7 are out of stock in the seed data)
        assertThat(inStock.size())
                .as("In-stock list should be smaller than full list")
                .isLessThan(all.size());

        // prod-4 and prod-7 are out of stock — they must NOT appear in the result
        assertThat(inStock).noneMatch(p -> p.id().equals("prod-4"));
        assertThat(inStock).noneMatch(p -> p.id().equals("prod-7"));
    }
}
