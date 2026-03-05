package com.example.liquibasemigrations.repository;

import com.example.liquibasemigrations.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sliced integration test for {@link ProductRepository}.
 *
 * <p>
 * {@code @DataJpaTest} configures only the JPA-related slice of the application
 * context: it loads entities, repositories, and an embedded database, but skips
 * web controllers and service components. This keeps the test focused and fast.
 * </p>
 *
 * <p>
 * {@code @AutoConfigureTestDatabase(replace = NONE)} tells Spring NOT to
 * replace
 * the configured datasource with a temporary embedded one. This allows our
 * {@code src/test/resources/application.properties} to supply the H2
 * datasource,
 * which is what we want so that Liquibase can run its changelogs on H2.
 * </p>
 *
 * <p>
 * Liquibase is auto-configured by Spring Boot and runs all changelogs before
 * the first test method executes. This means the test database is always
 * migrated
 * to the latest version, exactly as production would be.
 * </p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    // ---------------------------------------------------------
    // Test 1: Verify that Liquibase changesets 001, 002, and 003
    // were all applied correctly.
    // ---------------------------------------------------------

    @Test
    void liquibaseChangesetsApplied_seedDataExists() {
        // Changeset 002 inserts 3 products; we verify at least 3 exist
        List<Product> products = productRepository.findAll();
        assertThat(products).hasSizeGreaterThanOrEqualTo(3);

        // Verify that the seed product "Wireless Headphones" exists
        Optional<Product> headphones = products.stream()
                .filter(p -> "Wireless Headphones".equals(p.getName()))
                .findFirst();

        assertThat(headphones).isPresent();

        // Changeset 003 back-filled category = "Electronics" for all seed rows
        assertThat(headphones.get().getCategory()).isEqualTo("Electronics");

        // Verify the price was stored correctly as a DECIMAL
        // BigDecimal.compareTo is used because 79.99 == 79.99 (scale-independent)
        assertThat(headphones.get().getPrice().compareTo(new BigDecimal("79.99"))).isEqualTo(0);
    }

    // ---------------------------------------------------------
    // Test 2: Standard CRUD — save and retrieve a new product.
    // ---------------------------------------------------------

    @Test
    void saveAndFindProduct_success() {
        // Arrange: create a new product (not in seed data).
        // BigDecimal("99.99") is used instead of 99.99 (double) because the
        // Product entity's price field is BigDecimal to match the DECIMAL(10,2) column.
        Product product = new Product("Smart Speaker", new BigDecimal("99.99"), "Smart Home");

        // Act: persist and then retrieve by the generated ID
        Product saved = productRepository.save(product);
        Optional<Product> found = productRepository.findById(saved.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Smart Speaker");
        assertThat(found.get().getPrice().compareTo(new BigDecimal("99.99"))).isEqualTo(0);
        assertThat(found.get().getCategory()).isEqualTo("Smart Home");
    }

    // ---------------------------------------------------------
    // Test 3: Derived query — findByCategory.
    // ---------------------------------------------------------

    @Test
    void findByCategory_returnsMatchingProducts() {
        // Arrange: the 3 seed products all have category "Electronics" (from changeset
        // 003)
        // We also save a custom product in a different category
        productRepository.save(new Product("Notebook", new BigDecimal("5.99"), "Stationery"));

        // Act
        List<Product> electronics = productRepository.findByCategory("Electronics");
        List<Product> stationery = productRepository.findByCategory("Stationery");

        // Assert
        // Electronics should include the 3 seed products
        assertThat(electronics).hasSizeGreaterThanOrEqualTo(3);
        assertThat(stationery).hasSize(1);
        assertThat(stationery.get(0).getName()).isEqualTo("Notebook");
    }

    // ---------------------------------------------------------
    // Test 4: Delete a product.
    // ---------------------------------------------------------

    @Test
    void deleteProduct_removesItFromDatabase() {
        // Arrange: save a product, then delete it
        Product product = productRepository.save(
                new Product("Post-it Notes", new BigDecimal("3.49"), "Stationery"));
        Long id = product.getId();

        // Act
        productRepository.deleteById(id);

        // Assert
        assertThat(productRepository.findById(id)).isEmpty();
    }
}
