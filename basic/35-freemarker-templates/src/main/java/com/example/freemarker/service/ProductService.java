package com.example.freemarker.service;

import com.example.freemarker.model.Product;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer that provides product data to the controllers.
 *
 * <p>
 * In a real application this would query a database via a JPA repository.
 * Here we use a hard-coded in-memory list so the project stays focused on
 * FreeMarker template rendering concepts without introducing persistence
 * complexity.
 * </p>
 *
 * <p>
 * {@code @Service} marks this class as a Spring-managed bean, making it
 * eligible for dependency injection (e.g. via constructor injection in the
 * controller).
 * </p>
 */
@Service
public class ProductService {

    /**
     * Static, in-memory product catalog used as sample data.
     * The list is immutable ({@code List.of}) to make it clear that this
     * layer never mutates data — it only reads and returns it.
     */
    private static final List<Product> PRODUCTS = List.of(
            new Product(1L, "Wireless Headphones", "Electronics", 79.99, true),
            new Product(2L, "Mechanical Keyboard", "Electronics", 129.99, true),
            new Product(3L, "USB-C Hub", "Electronics", 49.99, false),
            new Product(4L, "Clean Code (Book)", "Books", 34.99, true),
            new Product(5L, "Spring in Action", "Books", 44.99, true),
            new Product(6L, "Ergonomic Mouse", "Electronics", 59.99, false),
            new Product(7L, "Desk Lamp", "Office", 29.99, true),
            new Product(8L, "Standing Desk Mat", "Office", 39.99, true));

    /**
     * Returns all products in the catalog.
     *
     * @return unmodifiable list of all {@link Product} objects
     */
    public List<Product> findAll() {
        return PRODUCTS;
    }

    /**
     * Finds a single product by its ID.
     *
     * @param id the product ID to look up
     * @return an {@link Optional} containing the product if found, or empty
     *         otherwise
     */
    public Optional<Product> findById(Long id) {
        return PRODUCTS.stream()
                .filter(p -> p.id().equals(id))
                .findFirst();
    }

    /**
     * Returns only the products that are currently in stock.
     *
     * @return list of in-stock products
     */
    public List<Product> findInStock() {
        return PRODUCTS.stream()
                .filter(Product::inStock)
                .toList();
    }

    /**
     * Returns products belonging to the given category (case-insensitive).
     *
     * @param category the category name to filter by
     * @return list of products in that category
     */
    public List<Product> findByCategory(String category) {
        return PRODUCTS.stream()
                .filter(p -> p.category().equalsIgnoreCase(category))
                .toList();
    }
}
