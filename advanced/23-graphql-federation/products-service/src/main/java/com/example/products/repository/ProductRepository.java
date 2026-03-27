package com.example.products.repository;

import com.example.products.model.Product;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for {@link Product} entities.
 *
 * <p>In a real production service this would delegate to a database (e.g., via
 * Spring Data JPA or R2DBC). For the purposes of this educational project, all
 * data lives in a {@link ConcurrentHashMap} that is pre-seeded with sample
 * products in the constructor. This keeps the project self-contained with no
 * external database dependency.
 *
 * <p>The {@link org.springframework.stereotype.Repository} annotation marks this
 * class as a Spring-managed data-access component. Spring Boot will auto-detect
 * it via component scanning and make it injectable into resolvers and services.
 */
@Repository
public class ProductRepository {

    /**
     * In-memory store: productId → Product.
     * ConcurrentHashMap is used for thread-safety (GraphQL resolvers may run
     * concurrently).
     */
    private final Map<String, Product> store = new ConcurrentHashMap<>();

    /**
     * Seed the in-memory store with a handful of sample products on construction.
     * Each product covers a distinct category so that category-based filter
     * demos are easy to demonstrate.
     */
    public ProductRepository() {
        List<Product> seed = List.of(
                new Product("prod-1", "Wireless Keyboard",
                        "Compact Bluetooth keyboard with backlit keys",
                        new BigDecimal("79.99"), "Electronics", true),
                new Product("prod-2", "Ergonomic Mouse",
                        "Vertical ergonomic mouse reducing wrist strain",
                        new BigDecimal("49.99"), "Electronics", true),
                new Product("prod-3", "Standing Desk",
                        "Height-adjustable sit-stand desk (120 x 60 cm)",
                        new BigDecimal("349.00"), "Furniture", true),
                new Product("prod-4", "Monitor Arm",
                        "Single monitor arm with full range of motion",
                        new BigDecimal("89.00"), "Furniture", false),
                new Product("prod-5", "USB-C Hub",
                        "7-in-1 USB-C hub with HDMI, SD card and power delivery",
                        new BigDecimal("39.99"), "Electronics", true),
                new Product("prod-6", "Noise Cancelling Headphones",
                        "Over-ear ANC headphones with 30-hour battery",
                        new BigDecimal("199.00"), "Electronics", true),
                new Product("prod-7", "Mechanical Keyboard",
                        "Full-size mechanical keyboard with Cherry MX switches",
                        new BigDecimal("129.99"), "Electronics", false),
                new Product("prod-8", "Desk Lamp",
                        "LED desk lamp with adjustable colour temperature",
                        new BigDecimal("34.99"), "Furniture", true)
        );
        seed.forEach(p -> store.put(p.id(), p));
    }

    /**
     * Returns all products in the catalogue.
     *
     * @return an unordered list of all products
     */
    public List<Product> findAll() {
        return List.copyOf(store.values());
    }

    /**
     * Looks up a single product by its unique identifier.
     *
     * @param id the product ID to search for
     * @return an {@link Optional} containing the product if found, or empty
     */
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Returns all products that belong to the given category (case-insensitive).
     *
     * @param category the category name to filter by
     * @return a list of matching products (may be empty)
     */
    public List<Product> findByCategory(String category) {
        return store.values().stream()
                .filter(p -> p.category().equalsIgnoreCase(category))
                .toList();
    }

    /**
     * Returns all products that are currently in stock.
     *
     * @return a list of products with {@code inStock == true}
     */
    public List<Product> findInStock() {
        return store.values().stream()
                .filter(Product::inStock)
                .toList();
    }
}
