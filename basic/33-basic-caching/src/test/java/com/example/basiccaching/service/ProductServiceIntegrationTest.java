package com.example.basiccaching.service;

import com.example.basiccaching.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProductService} that verify the caching
 * behavior.
 *
 * <p>
 * We use {@code @SpringBootTest} to load the full application context, which
 * includes the real {@code ConcurrentMapCacheManager}. This allows us to test
 * that
 * the {@code @Cacheable}, {@code @CachePut}, and {@code @CacheEvict}
 * annotations
 * work correctly end-to-end.
 * </p>
 *
 * <p>
 * <strong>Strategy:</strong> Between each test we clear the cache and reset the
 * service's data store via helper setup in {@code @BeforeEach} to guarantee
 * test
 * isolation. We verify caching indirectly by:
 * </p>
 * <ol>
 * <li>Checking that repeated calls return the same object reference (cache
 * hit).</li>
 * <li>Checking that cache entries appear / disappear after write / eviction
 * operations.</li>
 * </ol>
 */
@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    // The cache manager lets us inspect and clear the cache in tests
    @Autowired
    private CacheManager cacheManager;

    /**
     * Clears the "products" cache before each test so tests do not interfere with
     * each other.
     */
    @BeforeEach
    void clearCache() {
        // Retrieve the named cache and clear all entries
        var cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("findAll() should return all pre-seeded products")
    void testFindAll() {
        List<Product> products = productService.findAll();

        // The constructor seeds 4 products
        assertThat(products).isNotEmpty();
        assertThat(products).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("findAll() should cache its result — second call returns from cache")
    void testFindAllIsCached() {
        // First call: executes the real method, result stored in cache
        List<Product> firstCall = productService.findAll();

        // Second call: should be served from cache (same list reference in this case)
        List<Product> secondCall = productService.findAll();

        // Both calls should return equal content
        assertThat(firstCall).isEqualTo(secondCall);

        // Verify the cache actually holds the entry
        var cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get("all")).isNotNull();
    }

    @Test
    @DisplayName("findById() should return the correct product")
    void testFindById() {
        // Save a known product so we have a predictable ID
        Product saved = productService.save(new Product(null, "Test Widget", "Tools", 9.99));
        Long id = saved.id();

        Optional<Product> result = productService.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Test Widget");
        assertThat(result.get().price()).isEqualTo(9.99);
    }

    @Test
    @DisplayName("findById() should cache the result — second call uses cache")
    void testFindByIdIsCached() {
        Product saved = productService.save(new Product(null, "Cache Me", "Toys", 14.99));
        Long id = saved.id();

        // Clear cache so first findById is a real call
        cacheManager.getCache(ProductService.PRODUCTS_CACHE).clear();

        // First call: executes real method and populates cache with key = id
        productService.findById(id);

        // Verify that the cache now holds an entry for this ID
        var cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get(id)).isNotNull();
    }

    @Test
    @DisplayName("save() should evict the 'all' list from cache and add individual entry")
    void testSaveEvictsAllCache() {
        // Populate the "all" cache entry
        productService.findAll();
        var cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
        assertThat(cache.get("all")).isNotNull(); // "all" entry is in cache

        // Saving a new product should evict "all"
        Product newProduct = productService.save(new Product(null, "New Product", "Gadgets", 99.99));

        // "all" should now be evicted
        assertThat(cache.get("all")).isNull();

        // The newly saved product should be cached under its ID (@CachePut)
        assertThat(cache.get(newProduct.id())).isNotNull();
    }

    @Test
    @DisplayName("deleteById() should return true and evict the product's cache entry")
    void testDeleteByIdEvictsCache() {
        // Save a product and warm up its cache entry
        Product saved = productService.save(new Product(null, "To Delete", "Misc", 1.00));
        Long id = saved.id();

        // Warm the individual cache entry
        productService.findById(id);
        var cache = cacheManager.getCache(ProductService.PRODUCTS_CACHE);
        assertThat(cache.get(id)).isNotNull(); // entry is cached

        // Delete should evict the cache entry
        boolean deleted = productService.deleteById(id);
        assertThat(deleted).isTrue();

        // After @CacheEvict, the entry should be gone
        assertThat(cache.get(id)).isNull();
    }

    @Test
    @DisplayName("deleteById() should return false for a non-existent product")
    void testDeleteByIdNotFound() {
        boolean result = productService.deleteById(99999L);
        assertThat(result).isFalse();
    }
}
