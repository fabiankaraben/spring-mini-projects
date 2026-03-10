package com.example.entitylifecycle.repository;

import com.example.entitylifecycle.entity.Product;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProductRepository} and — most importantly —
 * the JPA entity lifecycle event callbacks.
 *
 * <p><b>What is being tested here:</b>
 * <ul>
 *   <li>{@code @PrePersist}  — slug is generated automatically before INSERT;
 *       {@code createdAt} and {@code updatedAt} are stamped before INSERT.</li>
 *   <li>{@code @PostLoad}    — {@code discountedPrice} is computed automatically
 *       after every SELECT; the value is never stored in the DB.</li>
 *   <li>{@code createdAt}    — remains unchanged after an UPDATE
 *       ({@code updatable = false} column constraint).</li>
 *   <li>Custom repository query methods ({@code findBySlug},
 *       {@code findByDiscountPercentGreaterThan}) work correctly against a real
 *       PostgreSQL database.</li>
 * </ul>
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @DataJpaTest} — loads a minimal Spring context containing only
 *       JPA-related components (entities, repositories, JPA infrastructure).
 *       No web layer, no service beans, no full application context.
 *       By default it replaces the real DataSource with an in-memory one; we
 *       override that with Testcontainers below.</li>
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — tells
 *       {@code @DataJpaTest} NOT to replace our configured DataSource with an
 *       embedded in-memory database, so Testcontainers' PostgreSQL is used.</li>
 *   <li>{@code @Testcontainers} — activates Testcontainers JUnit 5 extension,
 *       which manages the Docker container lifecycle (start before tests, stop
 *       after tests).</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryIntegrationTest {

    /**
     * The Testcontainers PostgreSQL container.
     *
     * <p>{@code @Container} on a {@code static} field makes Testcontainers start
     * the container once for the entire test class (shared container) rather than
     * once per test method. This is much faster because Docker image pull and
     * container startup happen only once.
     *
     * <p>The {@code postgres:16-alpine} image is the same one used in
     * {@code docker-compose.yml}, ensuring parity between tests and production.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("productsdb_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    /**
     * Dynamically registers the Testcontainers DataSource connection properties
     * into the Spring application context.
     *
     * <p>Testcontainers assigns a random host port to the container to avoid
     * conflicts. {@code @DynamicPropertySource} lets us read that port at runtime
     * and override the {@code spring.datasource.*} properties before the
     * ApplicationContext is created — without hard-coding any port numbers.
     *
     * @param registry the registry to add properties to
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Override the DataSource to point to the Testcontainers PostgreSQL instance
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Use 'create-drop' so Hibernate creates the schema fresh for each test run
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProductRepository productRepository;

    /**
     * Clears all products from the database before each test to guarantee test
     * isolation — no leftover data from a previous test can affect the next one.
     */
    @BeforeEach
    void cleanUp() {
        productRepository.deleteAll();
    }

    // =========================================================================
    // @PrePersist — slug generation
    // =========================================================================

    @Test
    @DisplayName("@PrePersist auto-generates a slug from the product name on INSERT")
    void save_newProduct_generatesSlugFromName() {
        // given — a product with no slug set
        Product product = new Product(
                "Wireless Headphones", "Great sound", new BigDecimal("99.99"), 20);

        // when — @PrePersist fires before the INSERT
        Product saved = productRepository.save(product);

        // then — slug must be auto-generated from the name
        assertThat(saved.getSlug())
                .as("Slug should be auto-generated from the product name by @PrePersist")
                .isEqualTo("wireless-headphones");
    }

    @Test
    @DisplayName("@PrePersist generates a slug that normalises special characters")
    void save_newProduct_generatesNormalisedSlug() {
        // given
        Product product = new Product(
                "Smart TV (65\") — 4K Pro", "Description", new BigDecimal("799.99"), 0);

        // when
        Product saved = productRepository.save(product);

        // then — special characters are converted to hyphens, no double-hyphens
        assertThat(saved.getSlug())
                .doesNotContain("  ")
                .doesNotStartWith("-")
                .doesNotEndWith("-")
                .matches("[a-z0-9-]+"); // only lowercase alphanumeric and hyphens
    }

    @Test
    @DisplayName("slug is preserved across updates — updatable = false column constraint")
    void update_existingProduct_slugDoesNotChange() {
        // given — persist a product and record its original slug
        Product product = new Product(
                "Original Name", "Desc", new BigDecimal("50.00"), 0);
        Product saved = productRepository.save(product);
        String originalSlug = saved.getSlug();

        // when — update the product name; the new name would produce a different slug
        saved.setName("Completely Different Name");
        saved.setUpdatedAt(Instant.now());
        Product updated = productRepository.saveAndFlush(saved);

        // then — slug must NOT have changed because its column is updatable = false
        assertThat(updated.getSlug())
                .as("Slug must remain unchanged after an UPDATE (updatable = false)")
                .isEqualTo(originalSlug);
    }

    // =========================================================================
    // @PrePersist — timestamp initialisation
    // =========================================================================

    @Test
    @DisplayName("@PrePersist sets createdAt and updatedAt to a non-null instant on INSERT")
    void save_newProduct_setsTimestamps() {
        // given
        Product product = new Product(
                "Monitor", "4K display", new BigDecimal("399.99"), 0);

        Instant before = Instant.now();

        // when — @PrePersist fires before the INSERT
        Product saved = productRepository.save(product);

        Instant after = Instant.now();

        // then — both timestamps must be set and fall within the expected range
        assertThat(saved.getCreatedAt())
                .as("createdAt should be set by @PrePersist")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);

        assertThat(saved.getUpdatedAt())
                .as("updatedAt should be set by @PrePersist")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("@PrePersist sets createdAt equal to updatedAt on initial INSERT")
    void save_newProduct_createdAtEqualsUpdatedAtOnInsert() {
        // given
        Product product = new Product("Webcam", "HD", new BigDecimal("79.99"), 0);

        // when
        Product saved = productRepository.save(product);

        // then — on first save both timestamps should be the same instant
        assertThat(saved.getCreatedAt())
                .isEqualTo(saved.getUpdatedAt());
    }

    @Test
    @DisplayName("createdAt is immutable — it does not change after an UPDATE")
    void update_existingProduct_createdAtDoesNotChange() throws InterruptedException {
        // given — persist a new product and record the initial createdAt
        Product product = new Product("Keyboard", "Mechanical", new BigDecimal("120.00"), 0);
        Product saved = productRepository.save(product);

        Instant originalCreatedAt = saved.getCreatedAt();
        assertThat(originalCreatedAt).isNotNull();

        // Sleep briefly so the clock advances before the update
        Thread.sleep(10);

        // when — update the product; createdAt column is updatable = false
        saved.setName("Mechanical Keyboard v2");
        saved.setUpdatedAt(Instant.now());
        Product updated = productRepository.saveAndFlush(saved);

        // then — createdAt must NOT have changed
        assertThat(updated.getCreatedAt())
                .as("createdAt must remain unchanged after an UPDATE (updatable = false)")
                .isEqualTo(originalCreatedAt);
    }

    // =========================================================================
    // @PostLoad — discounted price calculation
    // =========================================================================

    @Test
    @DisplayName("@PostLoad computes discountedPrice when entity is loaded from DB")
    void findById_triggersPostLoad_computesDiscountedPrice() {
        // given — persist a product with a 25% discount
        Product product = new Product(
                "Tablet", "Great display", new BigDecimal("200.00"), 25);
        Product saved = productRepository.save(product);
        Long id = saved.getId();

        // Clear the persistence context so the next findById triggers a real DB load
        // and fires @PostLoad. Without this, Hibernate may return the cached entity.
        productRepository.flush();

        // when — load the entity fresh from the DB; @PostLoad fires
        Optional<Product> loaded = productRepository.findById(id);

        // then — discountedPrice must be computed: 200.00 * (1 - 25/100) = 150.00
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getDiscountedPrice())
                .as("@PostLoad must compute discountedPrice as 150.00 for 25% off $200.00")
                .isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("@PostLoad computes discountedPrice = price when discount is 0")
    void findById_triggersPostLoad_zeroDiscount_priceUnchanged() {
        // given
        Product product = new Product(
                "USB Hub", "7 ports", new BigDecimal("49.99"), 0);
        Product saved = productRepository.save(product);

        // when
        Optional<Product> loaded = productRepository.findById(saved.getId());

        // then — no discount → discountedPrice equals the base price
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getDiscountedPrice())
                .as("@PostLoad must return discountedPrice equal to price when discount is 0%")
                .isEqualByComparingTo("49.99");
    }

    @Test
    @DisplayName("@PostLoad recomputes discountedPrice for every entity in findAll()")
    void findAll_triggersPostLoad_forEveryLoadedProduct() {
        // given — two products with different discounts
        productRepository.saveAll(List.of(
                new Product("Product A", "Desc A", new BigDecimal("100.00"), 10),
                new Product("Product B", "Desc B", new BigDecimal("200.00"), 20)
        ));

        // when — findAll triggers @PostLoad on every entity
        List<Product> all = productRepository.findAll();

        // then — each product must have a non-null discountedPrice
        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(p ->
                assertThat(p.getDiscountedPrice())
                        .as("Every loaded product must have a computed discountedPrice")
                        .isNotNull()
                        .isGreaterThan(BigDecimal.ZERO)
        );
    }

    // =========================================================================
    // Repository custom query methods
    // =========================================================================

    @Test
    @DisplayName("findBySlug returns the product when the slug exists")
    void findBySlug_returnsProduct_whenSlugExists() {
        // given — slug is auto-generated by @PrePersist
        productRepository.save(
                new Product("Gaming Mouse", "High DPI", new BigDecimal("59.99"), 5));

        // when — look up by the expected slug
        Optional<Product> result = productRepository.findBySlug("gaming-mouse");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Gaming Mouse");
        // @PostLoad must have fired, populating discountedPrice
        assertThat(result.get().getDiscountedPrice()).isNotNull();
    }

    @Test
    @DisplayName("findBySlug returns empty Optional when slug does not exist")
    void findBySlug_returnsEmpty_whenSlugNotFound() {
        // when
        Optional<Product> result = productRepository.findBySlug("nonexistent-slug");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByDiscountPercentGreaterThan returns only products with higher discount")
    void findByDiscountPercentGreaterThan_returnsMatchingProducts() {
        // given — three products: two discounted, one full price
        productRepository.saveAll(List.of(
                new Product("Item A", "Desc", new BigDecimal("100.00"), 10), // discounted
                new Product("Item B", "Desc", new BigDecimal("200.00"), 30), // discounted
                new Product("Item C", "Desc", new BigDecimal("50.00"), 0)    // full price
        ));

        // when — find all products with discount > 0
        List<Product> discounted = productRepository.findByDiscountPercentGreaterThan(0);

        // then — only Item A and Item B qualify
        assertThat(discounted).hasSize(2);
        assertThat(discounted).allSatisfy(p ->
                assertThat(p.getDiscountPercent()).isGreaterThan(0)
        );
    }

    @Test
    @DisplayName("findByDiscountPercentGreaterThan returns empty list when no products are discounted")
    void findByDiscountPercentGreaterThan_returnsEmpty_whenNoDiscounts() {
        // given — all products at full price
        productRepository.saveAll(List.of(
                new Product("Item X", "Desc", new BigDecimal("100.00"), 0),
                new Product("Item Y", "Desc", new BigDecimal("200.00"), 0)
        ));

        // when
        List<Product> discounted = productRepository.findByDiscountPercentGreaterThan(0);

        // then
        assertThat(discounted).isEmpty();
    }

    // =========================================================================
    // Basic CRUD through the repository
    // =========================================================================

    @Test
    @DisplayName("findAll returns all persisted products")
    void findAll_returnsAllProducts() {
        // given
        productRepository.saveAll(List.of(
                new Product("Product One", "Desc 1", new BigDecimal("10.00"), 0),
                new Product("Product Two", "Desc 2", new BigDecimal("20.00"), 0)
        ));

        // when
        List<Product> all = productRepository.findAll();

        // then
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("deleteById removes the product from the database")
    void deleteById_removesProduct() {
        // given
        Product saved = productRepository.save(
                new Product("To Delete", "Desc", new BigDecimal("1.00"), 0));
        Long id = saved.getId();

        // when
        productRepository.deleteById(id);

        // then
        assertThat(productRepository.findById(id)).isEmpty();
    }
}
