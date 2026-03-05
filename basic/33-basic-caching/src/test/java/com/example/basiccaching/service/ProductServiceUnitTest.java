package com.example.basiccaching.service;

import com.example.basiccaching.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProductService} focusing on business logic in
 * isolation.
 *
 * <p>
 * <strong>Note on testing caching in unit tests:</strong> Spring's caching
 * annotations ({@code @Cacheable}, etc.) are implemented via AOP proxies. When
 * you
 * instantiate {@code ProductService} directly with {@code new}, no proxy is
 * applied and
 * caching annotations have no effect. Therefore, these unit tests focus on
 * verifying
 * the core business logic (data manipulation) rather than caching behavior
 * itself.
 * </p>
 *
 * <p>
 * Caching behavior is verified in {@link ProductServiceIntegrationTest} where
 * the
 * full Spring context (including AOP proxies and CacheManager) is active.
 * </p>
 *
 * <p>
 * We use {@code @Spy} on the real service so we can verify method calls while
 * still
 * executing the real implementation. No mocked repository exists here since
 * {@code ProductService} uses its own internal ConcurrentHashMap — it has no
 * repository
 * dependency.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {

    // @Spy creates a real instance but allows call verification
    @Spy
    private ProductService productService;

    @BeforeEach
    void resetState() {
        // ProductService is stateful (holds internal data), so we create a fresh
        // instance for each test to avoid cross-test pollution.
        productService = new ProductService();
    }

    @Test
    @DisplayName("save() should assign an ID to a new product")
    void testSaveAssignsId() {
        Product input = new Product(null, "Blue Pen", "Stationery", 1.50);

        Product saved = productService.save(input);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("Blue Pen");
        assertThat(saved.category()).isEqualTo("Stationery");
        assertThat(saved.price()).isEqualTo(1.50);
    }

    @Test
    @DisplayName("save() with explicit ID should preserve that ID")
    void testSaveWithExplicitId() {
        Product input = new Product(42L, "Red Pen", "Stationery", 1.75);

        Product saved = productService.save(input);

        assertThat(saved.id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("findAll() should include all seeded and newly saved products")
    void testFindAllContainsSavedProduct() {
        // ProductService seeds 4 products in its constructor
        int initialCount = productService.findAll().size();

        productService.save(new Product(null, "Extra Item", "Other", 5.00));

        List<Product> all = productService.findAll();
        assertThat(all).hasSize(initialCount + 1);
    }

    @Test
    @DisplayName("findById() should return empty Optional for non-existent ID")
    void testFindByIdReturnsEmpty() {
        Optional<Product> result = productService.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById() should return the product when it exists")
    void testFindByIdReturnsProduct() {
        Product saved = productService.save(new Product(null, "Gadget X", "Electronics", 199.99));

        Optional<Product> result = productService.findById(saved.id());

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Gadget X");
    }

    @Test
    @DisplayName("deleteById() should return true and remove product from data store")
    void testDeleteByIdSuccess() {
        Product saved = productService.save(new Product(null, "Disposable Item", "Misc", 0.99));
        Long id = saved.id();

        boolean deleted = productService.deleteById(id);
        assertThat(deleted).isTrue();

        // Verify the product is no longer retrievable
        Optional<Product> afterDelete = productService.findById(id);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @DisplayName("deleteById() should return false for an ID that does not exist")
    void testDeleteByIdNotFound() {
        boolean result = productService.deleteById(Long.MAX_VALUE);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Multiple saves should generate incrementing unique IDs")
    void testMultipleSavesGenerateUniqueIds() {
        Product p1 = productService.save(new Product(null, "Item A", "Cat1", 10.0));
        Product p2 = productService.save(new Product(null, "Item B", "Cat1", 20.0));
        Product p3 = productService.save(new Product(null, "Item C", "Cat1", 30.0));

        // IDs must be distinct
        assertThat(p1.id()).isNotEqualTo(p2.id());
        assertThat(p2.id()).isNotEqualTo(p3.id());
        assertThat(p1.id()).isNotEqualTo(p3.id());
    }
}
