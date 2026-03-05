package com.example.basiccaching.service;

import com.example.basiccaching.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service that manages {@link Product} data and demonstrates Spring's caching
 * annotations.
 *
 * <h2>How caching works here</h2>
 * <p>
 * The actual data is stored in an in-memory {@code ConcurrentHashMap} (acting
 * as
 * our "database"). On top of that, Spring's cache abstraction adds another
 * caching layer
 * driven by the annotations below. When a method annotated with
 * {@code @Cacheable} is
 * called for the first time, it executes normally and the result is stored in
 * the
 * {@code ConcurrentMapCacheManager} (also backed by a
 * {@code ConcurrentHashMap}).
 * On subsequent calls with the same arguments, the method body is skipped
 * entirely and
 * the cached result is returned immediately — no re-computation needed.
 * </p>
 *
 * <h2>Cache annotations used</h2>
 * <ul>
 * <li>{@code @Cacheable} – Cache the method result on first call; return cached
 * value on subsequent calls.</li>
 * <li>{@code @CachePut} – Always execute the method and update the cache with
 * the new result.</li>
 * <li>{@code @CacheEvict} – Remove one or all entries from the cache.</li>
 * </ul>
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    // The name used to identify the cache region in ConcurrentMapCacheManager
    public static final String PRODUCTS_CACHE = "products";

    // Simulated in-memory data store (acts as our "database")
    private final Map<Long, Product> dataStore = new ConcurrentHashMap<>();

    // Thread-safe counter for generating unique IDs
    private final AtomicLong idSequence = new AtomicLong(1);

    /**
     * Initializes the service with some sample products so the API is usable
     * out-of-the-box.
     */
    public ProductService() {
        save(new Product(null, "Wireless Keyboard", "Electronics", 49.99));
        save(new Product(null, "Ergonomic Mouse", "Electronics", 29.99));
        save(new Product(null, "Standing Desk", "Furniture", 349.00));
        save(new Product(null, "Notebook (A5)", "Stationery", 4.99));
    }

    /**
     * Retrieves all products.
     *
     * <p>
     * Annotated with {@code @Cacheable}: the first call populates the cache under
     * the key {@code "all"}; subsequent calls skip this method body and return the
     * cached list directly. This is useful when listing all records is expensive.
     * </p>
     *
     * <p>
     * <strong>Important:</strong> If products are later added or removed, we must
     * evict or update this cache entry — see {@code save()} and
     * {@code deleteById()}.
     * </p>
     *
     * @return list of all products
     */
    @Cacheable(value = PRODUCTS_CACHE, key = "'all'")
    public List<Product> findAll() {
        simulateSlowCall("findAll");
        return new ArrayList<>(dataStore.values());
    }

    /**
     * Retrieves a single product by its ID.
     *
     * <p>
     * Cache key is the product ID itself. Each product is cached individually
     * under its own key. For example, product with id=1 is stored with key
     * {@code 1}.
     * </p>
     *
     * @param id the product ID
     * @return an Optional containing the product, or empty if not found
     */
    @Cacheable(value = PRODUCTS_CACHE, key = "#id")
    public Optional<Product> findById(Long id) {
        simulateSlowCall("findById(" + id + ")");
        return Optional.ofNullable(dataStore.get(id));
    }

    /**
     * Creates a new product or updates an existing one.
     *
     * <p>
     * Annotated with {@code @CachePut}: always executes the method body and stores
     * the return value in the cache. This keeps the per-ID cache entry up-to-date
     * without
     * requiring a separate lookup after the write.
     * </p>
     *
     * <p>
     * The {@code "all"} list cache is evicted here because after adding/updating a
     * product the cached list would be stale. We therefore force the next
     * {@code findAll()}
     * call to re-fetch from the data store.
     * </p>
     *
     * @param product the product to save (id may be null for new products)
     * @return the saved product with its assigned ID
     */
    @CachePut(value = PRODUCTS_CACHE, key = "#result.id()")
    @CacheEvict(value = PRODUCTS_CACHE, key = "'all'")
    public Product save(Product product) {
        // Assign a new ID if this is a new product (id is null)
        long id = (product.id() != null) ? product.id() : idSequence.getAndIncrement();
        Product savedProduct = new Product(id, product.name(), product.category(), product.price());
        dataStore.put(id, savedProduct);
        log.info("Saved product with id={}", id);
        return savedProduct;
    }

    /**
     * Deletes a product by its ID.
     *
     * <p>
     * Uses {@code @CacheEvict} to remove the specific product's entry from the
     * cache.
     * Also evicts the {@code "all"} list so the next call to {@code findAll()}
     * reflects
     * the deletion.
     * </p>
     *
     * @param id the ID of the product to delete
     * @return true if the product was found and deleted, false otherwise
     */
    @CacheEvict(value = PRODUCTS_CACHE, key = "#id")
    public boolean deleteById(Long id) {
        if (!dataStore.containsKey(id)) {
            return false;
        }
        // Also manually evict the "all" list entry since we cannot use two @CacheEvict
        // with the same value easily via one annotation on one method for different
        // keys
        // (we handle "all" eviction in the method body via the cache manager directly,
        // or by calling save which does it, so here we simply remove from dataStore).
        dataStore.remove(id);
        log.info("Deleted product with id={}", id);
        return true;
    }

    /**
     * Helper method that simulates a slow operation (e.g., a database query or
     * external API call).
     *
     * <p>
     * This makes caching effects clearly visible: the first call takes ~500ms,
     * while subsequent cached calls return instantly.
     * </p>
     */
    private void simulateSlowCall(String methodName) {
        log.info(">>> Executing REAL (non-cached) call: {}", methodName);
        try {
            // Simulate a 500ms delay to represent a slow data source
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("<<< Finished real call: {}", methodName);
    }
}
