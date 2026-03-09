package com.example.redisdatacache.controller;

import com.example.redisdatacache.domain.Product;
import com.example.redisdatacache.dto.ProductRequest;
import com.example.redisdatacache.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing CRUD endpoints for the {@link Product} resource.
 *
 * <p>All business logic and caching concerns are delegated to {@link ProductService};
 * this class is responsible only for:
 * <ul>
 *   <li>Mapping HTTP verbs and paths to service calls.</li>
 *   <li>Triggering Bean Validation via {@code @Valid} on request bodies.</li>
 *   <li>Converting service results to appropriate HTTP responses.</li>
 * </ul>
 *
 * <p>Base path: {@code /api/products}
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection makes the dependency explicit and testable without
     * the Spring application context.
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ── GET /api/products ─────────────────────────────────────────────────────────

    /**
     * List all products.
     *
     * <p>The first call triggers a cache miss and fetches data from the repository
     * (slow, ~200 ms with simulated delay). Subsequent calls within the TTL window
     * are served directly from Redis (fast, &lt;5 ms).
     *
     * @return 200 OK with the list of all products
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.findAll();
        return ResponseEntity.ok(products);
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    /**
     * Get a single product by ID.
     *
     * <p>Returns 404 Not Found if no product exists with the given ID.
     *
     * @param id the product ID from the URL path
     * @return 200 OK with the product, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Optional<Product> product = productService.findById(id);
        // Use map/orElse to convert Optional to ResponseEntity without if/else
        return product
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/products ────────────────────────────────────────────────────────

    /**
     * Create a new product.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body before the
     * method is invoked. Invalid requests receive a 400 Bad Request response with
     * field-level error details (provided automatically by Spring Boot's default
     * error handler).
     *
     * @param request the product data from the request body
     * @return 201 Created with the persisted product (including its generated ID)
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        // HTTP 201 Created is the semantically correct status for a successful POST
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────────

    /**
     * Update an existing product.
     *
     * <p>A full replacement (PUT) is performed: all fields in the request body
     * replace the existing values. Returns 404 if the product is not found.
     *
     * @param id      the product ID from the URL path
     * @param request the new product values from the request body
     * @return 200 OK with the updated product, or 404 Not Found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        Product updated = productService.update(id, request);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────────

    /**
     * Delete a product by ID.
     *
     * <p>Also evicts the corresponding Redis cache entries so stale data is never
     * returned (this eviction happens transparently inside {@link ProductService}).
     *
     * @param id the product ID from the URL path
     * @return 204 No Content on success, or 404 Not Found if the product did not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        boolean deleted = productService.deleteById(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        // HTTP 204 No Content is the standard response for a successful DELETE
        return ResponseEntity.noContent().build();
    }
}
