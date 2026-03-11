package com.example.testcontainerspostgres.repository;

import com.example.testcontainerspostgres.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProductRepository}.
 *
 * <p><b>What is an integration test (in this context)?</b>
 * Unlike unit tests that mock the database, integration tests run against a real
 * database. Here, <b>Testcontainers</b> starts a real PostgreSQL Docker container
 * automatically when the test class loads. The container is stopped after all
 * tests in the class finish. This guarantees:
 * <ul>
 *   <li>Real SQL is executed — derived queries, JPQL, and custom queries are
 *       all verified against actual PostgreSQL behaviour.</li>
 *   <li>Database constraints (NOT NULL, UNIQUE, etc.) are enforced.</li>
 *   <li>No manual Docker setup is required — the container lifecycle is managed
 *       entirely by the test framework.</li>
 * </ul>
 *
 * <p><b>Key annotations explained:</b>
 * <ul>
 *   <li>{@code @DataJpaTest} — loads a minimal Spring context containing only JPA
 *       components: entities, repositories, and the JPA/Hibernate infrastructure.
 *       No web layer, no service beans, no full application context. This makes
 *       the test context much faster to start than a full {@code @SpringBootTest}.
 *       By default, {@code @DataJpaTest} replaces the configured DataSource with
 *       an embedded H2 database. We override this below.</li>
 *
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — tells
 *       {@code @DataJpaTest} NOT to replace our DataSource with H2.
 *       This is critical: without it, Testcontainers' PostgreSQL would be ignored
 *       and tests would run against H2, defeating the whole purpose.</li>
 *
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension.
 *       It scans the class for fields annotated with {@code @Container} and manages
 *       their lifecycle (start before tests, stop after tests).</li>
 * </ul>
 *
 * <p><b>Container lifecycle — shared vs per-method:</b>
 * Declaring the container {@code static} with {@code @Container} makes it a
 * "shared container" — the Docker container starts once for the whole class,
 * not once per test method. This is much faster (container startup can take a
 * few seconds). Test isolation is maintained by the {@code @BeforeEach} method
 * that deletes all rows before each test.
 *
 * <p><b>Dynamic properties — {@code @DynamicPropertySource}:</b>
 * Testcontainers assigns a random free host port to the PostgreSQL container to
 * avoid port conflicts with other processes. We cannot hard-code this port in
 * {@code application.yml}. {@code @DynamicPropertySource} solves this: it runs
 * before the Spring context is created and registers properties programmatically
 * by reading the container's runtime values ({@code postgres.getJdbcUrl()}, etc.).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryIntegrationTest {

    /**
     * The PostgreSQL Docker container managed by Testcontainers.
     *
     * <p>Declared {@code static} so it is shared across all test methods in this
     * class — the container starts once and is reused for all tests (much faster).
     *
     * <p>{@code postgres:16-alpine} is the same image used in {@code docker-compose.yml},
     * ensuring test/production parity. The {@code alpine} variant is minimal, keeping
     * test startup time short.
     *
     * <p>The database name, username and password here are for the Testcontainers
     * instance only — they are separate from the values in {@code application.yml}
     * which target the production Docker Compose database.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productsdb_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    /**
     * Registers the Testcontainers DataSource properties into the Spring context
     * before it is initialised.
     *
     * <p>This is the bridge between Testcontainers (which knows the dynamic host
     * port) and Spring Data JPA (which needs the JDBC URL). Without this, Spring
     * would still try to connect to {@code localhost:5432} as configured in
     * {@code application.yml}.
     *
     * <p>We also override {@code ddl-auto} to {@code create-drop} so Hibernate
     * creates the schema fresh at the start of the test run and drops it afterwards,
     * giving us a clean slate every time.
     *
     * @param registry the property registry to populate
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Point Spring's DataSource to the Testcontainers-managed PostgreSQL instance
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // create-drop: Hibernate creates the schema before tests and drops it after
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProductRepository productRepository;

    /**
     * Clears all products before each test to ensure isolation.
     *
     * <p>Because the container is shared (static), data inserted by one test would
     * otherwise remain visible to the next test. Deleting all rows before each test
     * prevents such cross-test contamination.
     */
    @BeforeEach
    void cleanUp() {
        productRepository.deleteAll();
    }

    // =========================================================================
    // Basic CRUD operations
    // =========================================================================

    @Test
    @DisplayName("save persists a new product and assigns a database-generated ID")
    void save_persistsNewProduct_withGeneratedId() {
        // given — a new product with no ID (ID is assigned by the database)
        Product product = new Product("Laptop Pro", "High-performance laptop",
                new BigDecimal("999.99"), 10);

        // when — Hibernate issues INSERT and returns the managed entity with ID set
        Product saved = productRepository.save(product);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isGreaterThan(0L);
        assertThat(saved.getName()).isEqualTo("Laptop Pro");
        assertThat(saved.getPrice()).isEqualByComparingTo("999.99");
        assertThat(saved.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("findById returns the saved product when it exists")
    void findById_returnsProduct_whenExists() {
        // given — save a product first
        Product saved = productRepository.save(
                new Product("Tablet Z", "10-inch tablet", new BigDecimal("299.99"), 50));

        // when — retrieve it by the ID assigned after the INSERT
        Optional<Product> found = productRepository.findById(saved.getId());

        // then — the Optional must be present and hold the correct data
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tablet Z");
        assertThat(found.get().getPrice()).isEqualByComparingTo("299.99");
    }

    @Test
    @DisplayName("findById returns empty Optional when the product does not exist")
    void findById_returnsEmpty_whenNotFound() {
        // when — look up a product ID that was never inserted
        Optional<Product> found = productRepository.findById(9999L);

        // then — the Optional must be empty (not throw an exception)
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all persisted products")
    void findAll_returnsAllProducts() {
        // given — persist three products
        productRepository.saveAll(List.of(
                new Product("Product A", "Desc A", new BigDecimal("10.00"), 5),
                new Product("Product B", "Desc B", new BigDecimal("20.00"), 10),
                new Product("Product C", "Desc C", new BigDecimal("30.00"), 15)
        ));

        // when
        List<Product> all = productRepository.findAll();

        // then
        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("deleteById removes the product from the database")
    void deleteById_removesProduct() {
        // given
        Product saved = productRepository.save(
                new Product("To Delete", "Will be deleted", new BigDecimal("1.00"), 1));
        Long id = saved.getId();

        // when
        productRepository.deleteById(id);

        // then — a subsequent lookup must return empty
        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("save updates an existing product when re-saving with an ID")
    void save_updatesExistingProduct() {
        // given — persist and then modify the entity
        Product saved = productRepository.save(
                new Product("Old Name", "Old desc", new BigDecimal("50.00"), 20));

        saved.setName("New Name");
        saved.setPrice(new BigDecimal("75.00"));

        // when — save() on a managed entity with an existing ID issues an UPDATE
        Product updated = productRepository.saveAndFlush(saved);

        // then — the returned entity reflects the new values
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getPrice()).isEqualByComparingTo("75.00");

        // Re-query from the database to confirm the UPDATE was committed
        Product reloaded = productRepository.findById(updated.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("New Name");
        assertThat(reloaded.getPrice()).isEqualByComparingTo("75.00");
    }

    // =========================================================================
    // Custom derived query methods
    // =========================================================================

    @Test
    @DisplayName("findByName returns the matching product when it exists")
    void findByName_returnsProduct_whenExists() {
        // given
        productRepository.save(
                new Product("Unique Widget", "Special item", new BigDecimal("9.99"), 100));

        // when
        Optional<Product> result = productRepository.findByName("Unique Widget");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Unique Widget");
        assertThat(result.get().getPrice()).isEqualByComparingTo("9.99");
    }

    @Test
    @DisplayName("findByName returns empty Optional when name does not match")
    void findByName_returnsEmpty_whenNotFound() {
        // given — no product named "Ghost Product"
        productRepository.save(
                new Product("Real Product", "Exists", new BigDecimal("5.00"), 1));

        // when
        Optional<Product> result = productRepository.findByName("Ghost Product");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPriceLessThanEqual returns only products at or below the given price")
    void findByPriceLessThanEqual_returnsAffordableProducts() {
        // given — three products at different price points
        productRepository.saveAll(List.of(
                new Product("Budget Item", "Cheap", new BigDecimal("9.99"), 100),
                new Product("Mid Item", "Medium", new BigDecimal("49.99"), 50),
                new Product("Premium Item", "Expensive", new BigDecimal("199.99"), 10)
        ));

        // when — find all products priced at or below $50
        List<Product> result = productRepository.findByPriceLessThanEqual(new BigDecimal("50.00"));

        // then — only Budget Item and Mid Item qualify
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Budget Item", "Mid Item");
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase performs case-insensitive partial matching")
    void findByNameContainingIgnoreCase_matchesPartialName() {
        // given — two products with "phone" in the name (different cases)
        productRepository.saveAll(List.of(
                new Product("Smartphone X", "Android phone", new BigDecimal("499.99"), 20),
                new Product("iPhone Pro", "Apple phone", new BigDecimal("999.99"), 15),
                new Product("Laptop Pro", "Not a phone", new BigDecimal("1299.99"), 5)
        ));

        // when — search for "phone" (lowercase), should match both phone products
        List<Product> result = productRepository.findByNameContainingIgnoreCase("phone");

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Smartphone X", "iPhone Pro");
    }

    @Test
    @DisplayName("existsByName returns true when a product with the given name exists")
    void existsByName_returnsTrue_whenProductExists() {
        // given
        productRepository.save(
                new Product("Known Product", "Exists in DB", new BigDecimal("25.00"), 10));

        // when / then
        assertThat(productRepository.existsByName("Known Product")).isTrue();
    }

    @Test
    @DisplayName("existsByName returns false when no product with the given name exists")
    void existsByName_returnsFalse_whenProductNotFound() {
        // given — no product named "Unknown"
        productRepository.save(
                new Product("Another Product", "Different name", new BigDecimal("15.00"), 5));

        // when / then
        assertThat(productRepository.existsByName("Unknown")).isFalse();
    }

    // =========================================================================
    // Custom @Query methods
    // =========================================================================

    @Test
    @DisplayName("findAllInStock returns only products with stockQuantity > 0")
    void findAllInStock_returnsOnlyInStockProducts() {
        // given — two products in stock, one out of stock
        productRepository.saveAll(List.of(
                new Product("In Stock A", "Available", new BigDecimal("20.00"), 5),
                new Product("In Stock B", "Available", new BigDecimal("30.00"), 1),
                new Product("Out of Stock", "Sold out", new BigDecimal("40.00"), 0)
        ));

        // when
        List<Product> result = productRepository.findAllInStock();

        // then — only the two in-stock products are returned
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("In Stock A", "In Stock B");
        // Verify none of the returned products have zero stock
        assertThat(result).allSatisfy(p ->
                assertThat(p.getStockQuantity()).isGreaterThan(0));
    }

    @Test
    @DisplayName("findAllInStock returns empty list when all products are out of stock")
    void findAllInStock_returnsEmptyList_whenAllOutOfStock() {
        // given — all products have zero stock
        productRepository.saveAll(List.of(
                new Product("Sold Out A", "None left", new BigDecimal("10.00"), 0),
                new Product("Sold Out B", "None left", new BigDecimal("20.00"), 0)
        ));

        // when
        List<Product> result = productRepository.findAllInStock();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPriceBetween returns products within the inclusive price range")
    void findByPriceBetween_returnsProductsInRange() {
        // given — four products at different price points
        productRepository.saveAll(List.of(
                new Product("Cheap", "Below range", new BigDecimal("5.00"), 10),
                new Product("Low End", "At lower bound", new BigDecimal("10.00"), 10),
                new Product("Mid Range", "Within range", new BigDecimal("25.00"), 10),
                new Product("High End", "At upper bound", new BigDecimal("50.00"), 10),
                new Product("Expensive", "Above range", new BigDecimal("100.00"), 10)
        ));

        // when — search for products between $10.00 and $50.00 (inclusive)
        List<Product> result = productRepository.findByPriceBetween(
                new BigDecimal("10.00"), new BigDecimal("50.00"));

        // then — "Low End", "Mid Range" and "High End" should all qualify
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("Low End", "Mid Range", "High End");
    }

    @Test
    @DisplayName("findByPriceBetween returns empty list when no products are in the range")
    void findByPriceBetween_returnsEmptyList_whenNoMatch() {
        // given — all products are outside the target range
        productRepository.saveAll(List.of(
                new Product("Very Cheap", "Below", new BigDecimal("1.00"), 10),
                new Product("Very Expensive", "Above", new BigDecimal("10000.00"), 10)
        ));

        // when — search for products between $100 and $200
        List<Product> result = productRepository.findByPriceBetween(
                new BigDecimal("100.00"), new BigDecimal("200.00"));

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // Count and existence checks
    // =========================================================================

    @Test
    @DisplayName("count returns the correct number of products in the database")
    void count_returnsCorrectProductCount() {
        // given
        productRepository.saveAll(List.of(
                new Product("P1", "Desc", new BigDecimal("1.00"), 1),
                new Product("P2", "Desc", new BigDecimal("2.00"), 2),
                new Product("P3", "Desc", new BigDecimal("3.00"), 3)
        ));

        // when / then
        assertThat(productRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("existsById returns true for a product that was saved")
    void existsById_returnsTrue_forSavedProduct() {
        // given
        Product saved = productRepository.save(
                new Product("Existing", "Yes it is here", new BigDecimal("7.50"), 3));

        // when / then
        assertThat(productRepository.existsById(saved.getId())).isTrue();
    }

    @Test
    @DisplayName("existsById returns false after the product is deleted")
    void existsById_returnsFalse_afterDeletion() {
        // given — save and then delete
        Product saved = productRepository.save(
                new Product("Temporary", "Will be deleted", new BigDecimal("3.00"), 1));
        productRepository.deleteById(saved.getId());

        // when / then
        assertThat(productRepository.existsById(saved.getId())).isFalse();
    }
}
