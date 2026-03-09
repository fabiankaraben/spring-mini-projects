package com.example.redisdatacache.repository;

import com.example.redisdatacache.domain.Product;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory repository that simulates a slow data store.
 *
 * <p>In a real application this would be a JPA repository backed by a relational
 * database or another persistence layer. Here we intentionally use a simple
 * {@link ConcurrentHashMap} so that the project has zero external database
 * dependencies at runtime — the only external dependency is Redis for caching.
 *
 * <p>The {@link #findById(Long)} and {@link #findAll()} methods include a simulated
 * 200 ms delay to make the performance benefit of the Redis cache clearly visible
 * when you compare the first (cache-miss) call with subsequent (cache-hit) calls.
 */
@Repository
public class ProductRepository {

    /**
     * Thread-safe counter used to auto-generate unique IDs, analogous to a
     * database auto-increment sequence.
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    /**
     * The backing store; {@link ConcurrentHashMap} is used so the repository
     * is safe to call from multiple threads (e.g. concurrent integration tests).
     */
    private final Map<Long, Product> store = new ConcurrentHashMap<>();

    /**
     * Simulated latency in milliseconds added to read operations.
     * This value is exposed so that tests can set it to 0 to avoid slowing
     * down the test suite unnecessarily.
     */
    private long simulatedDelayMs = 200;

    // ── Seed data ─────────────────────────────────────────────────────────────────

    /**
     * Populate the store with a handful of sample products so that the API
     * returns useful data immediately after startup without any prior POST calls.
     */
    public ProductRepository() {
        save(new Product(null, "Laptop Pro 15", "High-performance laptop with 15-inch display", new BigDecimal("1299.99"), "electronics"));
        save(new Product(null, "Wireless Mouse", "Ergonomic wireless mouse with 12-month battery life", new BigDecimal("39.99"), "electronics"));
        save(new Product(null, "Java Programming Guide", "Comprehensive guide to Java 21 and modern JVM features", new BigDecimal("59.99"), "books"));
        save(new Product(null, "Standing Desk", "Height-adjustable standing desk for home offices", new BigDecimal("499.00"), "furniture"));
        save(new Product(null, "USB-C Hub", "7-in-1 USB-C hub with HDMI, Ethernet, and SD card reader", new BigDecimal("49.99"), "electronics"));
    }

    // ── CRUD operations ───────────────────────────────────────────────────────────

    /**
     * Persist a product and return the saved instance with its assigned ID.
     *
     * <p>If the product already has an ID it is treated as an update; otherwise a
     * new ID is generated from the sequence.
     *
     * @param product the product to persist
     * @return the saved product with a populated {@code id}
     */
    public Product save(Product product) {
        if (product.getId() == null) {
            // Assign the next ID from the sequence (like a DB auto-increment)
            product.setId(idSequence.getAndIncrement());
        }
        product.setLastModified(Instant.now());
        store.put(product.getId(), product);
        return product;
    }

    /**
     * Look up a product by its ID, simulating a slow database read.
     *
     * <p>The artificial delay makes it easy to observe the speedup provided by
     * the Redis cache: the first request takes ~200 ms; subsequent requests
     * return immediately from the cache.
     *
     * @param id the product ID
     * @return an {@link Optional} containing the product, or empty if not found
     */
    public Optional<Product> findById(Long id) {
        simulateDelay();
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Return all products, simulating a slow full-table scan.
     *
     * @return list of all products currently in the store
     */
    public List<Product> findAll() {
        simulateDelay();
        return new ArrayList<>(store.values());
    }

    /**
     * Remove a product by ID.
     *
     * @param id the product ID to delete
     * @return {@code true} if a product was removed, {@code false} if ID not found
     */
    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    /**
     * Check whether a product with the given ID exists (no simulated delay,
     * used internally before update/delete operations).
     */
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    // ── Test helpers ──────────────────────────────────────────────────────────────

    /**
     * Override the simulated delay. Set to {@code 0} in unit/integration tests to
     * keep the test suite fast.
     *
     * @param delayMs delay in milliseconds
     */
    public void setSimulatedDelayMs(long delayMs) {
        this.simulatedDelayMs = delayMs;
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Block the calling thread for {@link #simulatedDelayMs} milliseconds.
     * Interrupted exceptions are re-interrupted to honour the thread's interrupt flag.
     */
    private void simulateDelay() {
        if (simulatedDelayMs <= 0) return;
        try {
            Thread.sleep(simulatedDelayMs);
        } catch (InterruptedException e) {
            // Restore the interrupt flag so callers can handle it if needed
            Thread.currentThread().interrupt();
        }
    }
}
