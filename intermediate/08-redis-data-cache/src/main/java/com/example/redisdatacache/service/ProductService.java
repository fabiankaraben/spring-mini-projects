package com.example.redisdatacache.service;

import com.example.redisdatacache.domain.Product;
import com.example.redisdatacache.dto.ProductRequest;
import com.example.redisdatacache.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer containing the business logic for product management.
 *
 * <p>This class is the primary demonstration of Spring's cache abstraction.
 * Three annotation types are used:
 *
 * <ul>
 *   <li>{@link Cacheable} – before invoking the method, Spring checks whether
 *       a value is already stored under the computed cache key. If a cache hit
 *       occurs the method body is skipped entirely and the cached value is
 *       returned immediately (this is what produces the dramatic speedup).</li>
 *   <li>{@link CachePut} – always executes the method body and stores the
 *       result in the cache, effectively refreshing the entry. Used on updates
 *       so the cache stays consistent with the data store.</li>
 *   <li>{@link CacheEvict} – removes one or more entries from the cache. Used
 *       on deletes so stale data is never returned after a product is removed.</li>
 * </ul>
 *
 * <p>Cache names (e.g. {@code "products"}, {@code "products-all"}) are declared
 * in {@link com.example.redisdatacache.config.CacheConfig} with their respective
 * TTLs and serialisation settings.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Constructor injection is preferred over field injection because it makes
     * dependencies explicit and simplifies unit testing (no Spring context needed).
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve a single product by its ID with caching.
     *
     * <p>{@code @Cacheable(value = "products", key = "#id")} instructs Spring to:
     * <ol>
     *   <li>Build the cache key {@code products::<id>} (e.g. {@code products::1}).</li>
     *   <li>If a value exists in Redis under that key, return it immediately WITHOUT
     *       calling {@link ProductRepository#findById(Long)} (cache hit).</li>
     *   <li>If no value exists, call the repository, store the result in Redis,
     *       then return it (cache miss).</li>
     * </ol>
     *
     * <p>{@code unless = "#result == null"} prevents caching {@code null} values,
     * which would cause every subsequent call for a missing ID to return null from
     * cache even after the product is created.
     *
     * @param id the product ID
     * @return an {@link Optional} with the found product, or empty if not found
     */
    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Retrieve all products with caching.
     *
     * <p>The entire list is stored under the fixed key {@code "all"} in the
     * {@code "products-all"} cache. A separate cache name is used so that
     * evicting a single product (via {@link #deleteById(Long)}) can also
     * evict the list cache independently.
     *
     * <p>{@code unless = "#result.isEmpty()"} avoids caching an empty list
     * during the brief moment after startup before seed data is loaded.
     *
     * @return list of all products
     */
    @Cacheable(value = "products-all", key = "'all'", unless = "#result.isEmpty()")
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create a new product from the given request.
     *
     * <p>The {@code @CacheEvict} on {@code "products-all"} ensures the list cache
     * is invalidated so the next call to {@link #findAll()} fetches fresh data
     * from the repository (which now includes the new product).
     *
     * @param request the product creation request
     * @return the persisted product with its assigned ID
     */
    @CacheEvict(value = "products-all", allEntries = true)
    public Product create(ProductRequest request) {
        // Map the DTO to the domain model before persisting
        Product product = new Product(
                null,
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getCategory()
        );
        return productRepository.save(product);
    }

    /**
     * Update an existing product.
     *
     * <p>{@code @CachePut(value = "products", key = "#id")} always executes the
     * method body and writes the return value back into the {@code "products"}
     * cache under the same key. This keeps the single-product cache entry
     * consistent without forcing the next read to go to the data store.
     *
     * <p>The {@code @CacheEvict} on {@code "products-all"} invalidates the
     * list cache because the stored list would contain the outdated product.
     *
     * @param id      the ID of the product to update
     * @param request the new values for the product
     * @return the updated product, or {@code null} if the product does not exist
     */
    @Caching(
        put  = { @CachePut(value = "products", key = "#id", unless = "#result == null") },
        evict = { @CacheEvict(value = "products-all", allEntries = true) }
    )
    public Product update(Long id, ProductRequest request) {
        // If the product does not exist we cannot update it; return null
        Optional<Product> existing = productRepository.findById(id);
        if (existing.isEmpty()) {
            return null;
        }

        // Mutate the existing product in-place (keeps the same ID and object)
        Product product = existing.get();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());

        return productRepository.save(product);
    }

    /**
     * Delete a product and evict its cache entries.
     *
     * <p>{@link Caching} combines two {@code @CacheEvict} operations in a single
     * annotation block:
     * <ol>
     *   <li>Evict the individual product entry from the {@code "products"} cache.</li>
     *   <li>Evict all entries from the {@code "products-all"} cache so the list
     *       is refreshed on the next read.</li>
     * </ol>
     *
     * @param id the product ID to delete
     * @return {@code true} if the product was deleted, {@code false} if not found
     */
    @Caching(evict = {
        @CacheEvict(value = "products",     key = "#id"),
        @CacheEvict(value = "products-all", allEntries = true)
    })
    public boolean deleteById(Long id) {
        return productRepository.deleteById(id);
    }
}
