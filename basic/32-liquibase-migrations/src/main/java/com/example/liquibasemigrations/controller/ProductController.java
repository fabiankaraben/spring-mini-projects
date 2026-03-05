package com.example.liquibasemigrations.controller;

import com.example.liquibasemigrations.entity.Product;
import com.example.liquibasemigrations.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller that exposes HTTP endpoints for managing {@link Product}
 * resources.
 *
 * <p>
 * All routes are prefixed with {@code /api/products}. The controller is kept
 * thin: it delegates all persistence logic to the {@link ProductRepository}.
 * </p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    // Constructor injection is preferred over field injection because it:
    // 1. Makes dependencies explicit and visible.
    // 2. Allows easy mocking in unit tests without Spring context.
    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    // ---------------------------------------------------------
    // GET /api/products
    // Returns the complete list of products.
    // ---------------------------------------------------------

    /**
     * Retrieve all products stored in the database.
     *
     * @return HTTP 200 with the list of all products.
     */
    @GetMapping
    public List<Product> getAllProducts() {
        return repository.findAll();
    }

    // ---------------------------------------------------------
    // GET /api/products/{id}
    // Returns one product by its primary key.
    // ---------------------------------------------------------

    /**
     * Retrieve a single product by its database ID.
     *
     * @param id the primary-key of the product.
     * @return HTTP 200 with the product, or HTTP 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Optional<Product> product = repository.findById(id);
        // Use the Optional to map to 200 OK or produce 404 Not Found
        return product.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------
    // GET /api/products/category/{category}
    // Demonstrates using the derived query defined in the repository.
    // ---------------------------------------------------------

    /**
     * Retrieve products filtered by category.
     * Uses the {@code findByCategory} derived query in {@link ProductRepository}.
     *
     * @param category the category name to filter by.
     * @return HTTP 200 with matching products (may be empty).
     */
    @GetMapping("/category/{category}")
    public List<Product> getProductsByCategory(@PathVariable String category) {
        return repository.findByCategory(category);
    }

    // ---------------------------------------------------------
    // POST /api/products
    // Creates a new product and returns it with the generated ID.
    // ---------------------------------------------------------

    /**
     * Create a new product.
     *
     * @param product the product data from the request body (JSON).
     * @return HTTP 201 Created with the saved product (including its generated ID).
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        Product saved = repository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ---------------------------------------------------------
    // DELETE /api/products/{id}
    // Deletes a product or returns 404 if it does not exist.
    // ---------------------------------------------------------

    /**
     * Delete a product by ID.
     *
     * @param id the primary-key of the product to delete.
     * @return HTTP 204 No Content on success, or HTTP 404 if not found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
