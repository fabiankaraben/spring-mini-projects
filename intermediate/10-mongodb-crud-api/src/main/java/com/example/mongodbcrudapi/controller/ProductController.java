package com.example.mongodbcrudapi.controller;

import com.example.mongodbcrudapi.domain.Product;
import com.example.mongodbcrudapi.dto.ProductRequest;
import com.example.mongodbcrudapi.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing CRUD endpoints for the {@link Product} resource.
 *
 * <p>All business logic is delegated to {@link ProductService}; this class is
 * responsible only for:
 * <ul>
 *   <li>Mapping HTTP verbs and URL paths to service methods.</li>
 *   <li>Triggering Bean Validation via {@code @Valid} on request bodies.</li>
 *   <li>Converting service results to the appropriate HTTP status codes and
 *       response bodies.</li>
 * </ul>
 *
 * <p>Base path: {@code /api/products}
 *
 * <p>Endpoints summary:
 * <pre>
 *   GET    /api/products                     – list all products
 *   GET    /api/products/{id}                – get product by ID
 *   GET    /api/products/search?name=...     – search by name (substring)
 *   GET    /api/products/category/{category} – filter by category
 *   GET    /api/products/max-price?price=... – filter by max price
 *   GET    /api/products/low-stock?threshold – filter low-stock items
 *   POST   /api/products                     – create a new product
 *   PUT    /api/products/{id}                – update an existing product
 *   DELETE /api/products/{id}                – delete a product
 * </pre>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection makes the dependency explicit and testable without
     * the Spring application context (no reflection, no magic).
     *
     * @param productService the service containing business logic
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ── GET /api/products ─────────────────────────────────────────────────────────

    /**
     * List all products in the MongoDB collection.
     *
     * <p>HTTP 200 OK is always returned; an empty collection produces an empty
     * JSON array rather than a 404 response (empty list ≠ not found).
     *
     * @return 200 OK with a JSON array of all products
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.findAll();
        return ResponseEntity.ok(products);
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    /**
     * Get a single product by its MongoDB ObjectId.
     *
     * <p>Returns 404 Not Found if no document with the given ID exists in the
     * collection.
     *
     * @param id the product's MongoDB document ID (24-character hex string)
     * @return 200 OK with the product, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        Optional<Product> product = productService.findById(id);
        // map/orElse converts Optional<Product> to ResponseEntity without if/else
        return product
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/products/search?name=... ─────────────────────────────────────────

    /**
     * Search for products by name (case-insensitive substring match).
     *
     * <p>Example: {@code GET /api/products/search?name=laptop} returns all
     * products whose name contains "laptop" regardless of case.
     *
     * @param name the search term (partial match supported)
     * @return 200 OK with a list of matching products (empty list if no matches)
     */
    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchByName(@RequestParam String name) {
        List<Product> products = productService.searchByName(name);
        return ResponseEntity.ok(products);
    }

    // ── GET /api/products/category/{category} ────────────────────────────────────

    /**
     * List all products in a specific category.
     *
     * <p>Example: {@code GET /api/products/category/electronics} returns all
     * products with category equal to "electronics" (exact, case-sensitive match).
     *
     * @param category the category name to filter by
     * @return 200 OK with a list of products in the given category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable String category) {
        List<Product> products = productService.findByCategory(category);
        return ResponseEntity.ok(products);
    }

    // ── GET /api/products/max-price?price=... ────────────────────────────────────

    /**
     * List products priced at or below a given maximum.
     *
     * <p>Example: {@code GET /api/products/max-price?price=50.00} returns products
     * with a price ≤ 50.00. Useful for budget-constrained filtering.
     *
     * @param price the maximum price (inclusive)
     * @return 200 OK with a list of products within the price limit
     */
    @GetMapping("/max-price")
    public ResponseEntity<List<Product>> getByMaxPrice(@RequestParam BigDecimal price) {
        List<Product> products = productService.findByMaxPrice(price);
        return ResponseEntity.ok(products);
    }

    // ── GET /api/products/low-stock?threshold=... ────────────────────────────────

    /**
     * List products with stock quantity below a given threshold.
     *
     * <p>Example: {@code GET /api/products/low-stock?threshold=10} returns products
     * with fewer than 10 units in stock. Useful for inventory replenishment alerts.
     *
     * @param threshold the minimum stock level (exclusive); defaults to 10
     * @return 200 OK with a list of low-stock products
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<Product>> getLowStockProducts(
            @RequestParam(defaultValue = "10") int threshold) {
        List<Product> products = productService.findLowStockProducts(threshold);
        return ResponseEntity.ok(products);
    }

    // ── POST /api/products ────────────────────────────────────────────────────────

    /**
     * Create a new product document in MongoDB.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body. Invalid
     * requests (e.g. blank name, negative price) result in a 400 Bad Request
     * response with field-level error details provided by Spring Boot's default
     * error handler.
     *
     * @param request the product data from the HTTP request body (JSON)
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
     * Update (full replacement) of an existing product document.
     *
     * <p>PUT semantics: all mutable fields are replaced with the values in the
     * request body. Fields not included will be overwritten. The document's
     * {@code id} and {@code createdAt} are preserved.
     *
     * @param id      the product's MongoDB document ID from the URL path
     * @param request the new field values from the HTTP request body (JSON)
     * @return 200 OK with the updated product, or 404 Not Found if ID doesn't exist
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {

        Optional<Product> updated = productService.update(id, request);
        return updated
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────────

    /**
     * Delete a product document by its MongoDB ObjectId.
     *
     * @param id the product's MongoDB document ID from the URL path
     * @return 204 No Content on success, or 404 Not Found if the ID doesn't exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        boolean deleted = productService.deleteById(id);
        if (!deleted) {
            // Product with this ID was not found in MongoDB
            return ResponseEntity.notFound().build();
        }
        // HTTP 204 No Content is the standard response for a successful DELETE
        return ResponseEntity.noContent().build();
    }
}
