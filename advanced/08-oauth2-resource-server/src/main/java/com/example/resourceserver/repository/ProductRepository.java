package com.example.resourceserver.repository;

import com.example.resourceserver.domain.Product;

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
 * In-memory repository for {@link Product} objects.
 *
 * <p>This repository stores products in a {@link ConcurrentHashMap} to keep the
 * mini-project focused on OAuth2 Resource Server patterns without adding a database
 * dependency. In a real microservice, this class would be replaced by a Spring Data
 * JPA or Spring Data MongoDB repository backed by a real database.
 *
 * <p><b>Thread safety:</b>
 * {@link ConcurrentHashMap} provides thread-safe read and write operations, making
 * this repository safe for concurrent HTTP requests in a multi-threaded Spring MVC
 * application.
 *
 * <p><b>ID generation:</b>
 * {@link AtomicLong} provides a thread-safe auto-incrementing counter for product IDs.
 * This simulates the auto-increment behavior of a database sequence.
 *
 * <p>On application startup, several sample products are pre-loaded to make it easy
 * to test the API without needing to create data first.
 */
@Repository
public class ProductRepository {

    /**
     * The in-memory storage: maps product ID (Long) to Product objects.
     * ConcurrentHashMap is used instead of HashMap for thread safety.
     */
    private final Map<Long, Product> store = new ConcurrentHashMap<>();

    /**
     * Auto-incrementing ID counter.
     * AtomicLong ensures that concurrent product creation never produces
     * duplicate IDs (unlike a plain long field which would have a race condition).
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    /**
     * Initializes the repository with pre-loaded sample products.
     * These products let users test the API immediately after startup.
     */
    public ProductRepository() {
        loadSampleProducts();
    }

    /**
     * Returns all products currently in the store.
     *
     * @return a new list containing all products; never null, may be empty
     */
    public List<Product> findAll() {
        // Return a copy so external code cannot mutate the internal store
        return new ArrayList<>(store.values());
    }

    /**
     * Finds a product by its unique ID.
     *
     * @param id the product ID to look up
     * @return an {@link Optional} containing the product if found, or empty if not found
     */
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Returns all products belonging to the given category.
     *
     * <p>The comparison is case-insensitive so that "electronics" and "ELECTRONICS"
     * match the same products.
     *
     * @param category the category name to filter by
     * @return a list of matching products; may be empty
     */
    public List<Product> findByCategory(String category) {
        return store.values().stream()
                .filter(p -> p.getCategory() != null
                        && p.getCategory().equalsIgnoreCase(category))
                .toList();
    }

    /**
     * Persists a new product and assigns it a unique auto-incremented ID.
     *
     * <p>The {@code createdAt} and {@code updatedAt} timestamps are set here
     * to the current UTC instant.
     *
     * @param product the product to save (id field is ignored and overwritten)
     * @return the saved product with its generated ID set
     */
    public Product save(Product product) {
        // Generate a new unique ID from the atomic counter
        long newId = idSequence.getAndIncrement();
        product.setId(newId);

        Instant now = Instant.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        store.put(newId, product);
        return product;
    }

    /**
     * Updates an existing product in the store.
     *
     * <p>The {@code updatedAt} timestamp is refreshed to the current UTC instant.
     * This method assumes the caller has already set the correct ID on the product.
     *
     * @param product the product with updated values (must have an existing ID)
     * @return the updated product
     */
    public Product update(Product product) {
        // Refresh the updatedAt timestamp to the current time
        product.setUpdatedAt(Instant.now());
        store.put(product.getId(), product);
        return product;
    }

    /**
     * Deletes the product with the given ID.
     *
     * @param id the ID of the product to delete
     * @return {@code true} if a product was deleted, {@code false} if no product had that ID
     */
    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    /**
     * Returns the total count of products in the store.
     *
     * @return the number of stored products
     */
    public int count() {
        return store.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads a set of realistic sample products into the store on startup.
     *
     * <p>These products cover multiple categories (ELECTRONICS, BOOKS, HOME) and
     * demonstrate a variety of price points and stock levels. They allow API
     * consumers to immediately explore the endpoints without creating test data.
     */
    private void loadSampleProducts() {
        // Product 1: Electronics
        Product headphones = new Product();
        headphones.setName("Wireless Noise-Cancelling Headphones");
        headphones.setDescription("Premium over-ear headphones with 30-hour battery life, "
                + "active noise cancellation, and Bluetooth 5.2.");
        headphones.setPrice(new BigDecimal("149.99"));
        headphones.setCategory("ELECTRONICS");
        headphones.setStock(75);
        save(headphones);

        // Product 2: Electronics
        Product smartWatch = new Product();
        smartWatch.setName("Smartwatch Pro X");
        smartWatch.setDescription("Fitness tracker with heart rate monitor, GPS, "
                + "sleep tracking, and 7-day battery life. Water resistant to 50m.");
        smartWatch.setPrice(new BigDecimal("249.99"));
        smartWatch.setCategory("ELECTRONICS");
        smartWatch.setStock(40);
        save(smartWatch);

        // Product 3: Books
        Product springBook = new Product();
        springBook.setName("Spring Boot in Action");
        springBook.setDescription("A comprehensive guide to building production-ready "
                + "applications with Spring Boot. Covers REST APIs, security, "
                + "data access, and microservices.");
        springBook.setPrice(new BigDecimal("39.99"));
        springBook.setCategory("BOOKS");
        springBook.setStock(120);
        save(springBook);

        // Product 4: Books
        Product dddBook = new Product();
        dddBook.setName("Domain-Driven Design");
        dddBook.setDescription("Eric Evans' seminal work on tackling complexity in the "
                + "heart of software through domain modeling and ubiquitous language.");
        dddBook.setPrice(new BigDecimal("54.99"));
        dddBook.setCategory("BOOKS");
        dddBook.setStock(85);
        save(dddBook);

        // Product 5: Home
        Product coffeeMaker = new Product();
        coffeeMaker.setName("Programmable Coffee Maker");
        coffeeMaker.setDescription("12-cup programmable drip coffee maker with built-in "
                + "grinder, thermal carafe, and auto-clean feature.");
        coffeeMaker.setPrice(new BigDecimal("89.99"));
        coffeeMaker.setCategory("HOME");
        coffeeMaker.setStock(30);
        save(coffeeMaker);

        // Product 6: Home
        Product airPurifier = new Product();
        airPurifier.setName("HEPA Air Purifier");
        airPurifier.setDescription("True HEPA air purifier covering up to 360 sq ft. "
                + "Removes 99.97% of dust, pollen, smoke, and allergens.");
        airPurifier.setPrice(new BigDecimal("129.99"));
        airPurifier.setCategory("HOME");
        airPurifier.setStock(55);
        save(airPurifier);
    }
}
