package com.example.cassandraintegration.controller;

import com.example.cassandraintegration.domain.Product;
import com.example.cassandraintegration.dto.CreateProductRequest;
import com.example.cassandraintegration.dto.UpdateProductRequest;
import com.example.cassandraintegration.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller exposing endpoints for the Product catalog.
 *
 * <p>Base path: {@code /api/products}</p>
 *
 * <p>All query endpoints require the {@code category} parameter because
 * it is the Cassandra partition key — without it, Cassandra would have to
 * scan all partitions (which is inefficient for large datasets).</p>
 *
 * <h2>Endpoint Overview</h2>
 * <ul>
 *   <li>POST   /api/products                              — Create a product</li>
 *   <li>GET    /api/products?category=...                 — List by category</li>
 *   <li>GET    /api/products/{id}?category=...            — Get by composite key</li>
 *   <li>GET    /api/products/filter/price?category=...    — Filter by max price</li>
 *   <li>GET    /api/products/filter/stock?category=...    — Filter by min stock</li>
 *   <li>PUT    /api/products/{id}?category=...            — Update a product</li>
 *   <li>DELETE /api/products/{id}?category=...            — Delete a product</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** Service handling all product-related business logic. */
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Creates a new product in the Cassandra table.
     *
     * <pre>
     * POST /api/products
     * Body: { "category": "Electronics", "name": "Laptop", "price": 999.99, "stock": 50 }
     * </pre>
     *
     * @param request validated DTO with product fields
     * @return 201 Created with the persisted Product (including generated UUID)
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product saved = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Returns all products in a given category.
     *
     * <pre>
     * GET /api/products?category=Electronics
     * </pre>
     *
     * @param category the partition key (required)
     * @return 200 OK with list of products
     */
    @GetMapping
    public ResponseEntity<List<Product>> getByCategory(@RequestParam String category) {
        return ResponseEntity.ok(productService.findByCategory(category));
    }

    /**
     * Returns a single product by its composite primary key.
     *
     * <pre>
     * GET /api/products/{id}?category=Electronics
     * </pre>
     *
     * @param id       UUID (clustering key) path variable
     * @param category partition key query parameter
     * @return 200 OK with the Product, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable UUID id,
                                           @RequestParam String category) {
        return productService.findByCategoryAndId(category, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns products in a category whose price is at or below the given maximum.
     *
     * <pre>
     * GET /api/products/filter/price?category=Electronics&maxPrice=500
     * </pre>
     *
     * @param category the partition key
     * @param maxPrice upper price bound (inclusive)
     * @return 200 OK with matching products
     */
    @GetMapping("/filter/price")
    public ResponseEntity<List<Product>> filterByMaxPrice(@RequestParam String category,
                                                          @RequestParam BigDecimal maxPrice) {
        return ResponseEntity.ok(productService.findByPriceAtMost(category, maxPrice));
    }

    /**
     * Returns products in a category with stock above a minimum threshold.
     *
     * <pre>
     * GET /api/products/filter/stock?category=Electronics&minStock=10
     * </pre>
     *
     * @param category the partition key
     * @param minStock minimum stock level (exclusive lower bound)
     * @return 200 OK with matching products
     */
    @GetMapping("/filter/stock")
    public ResponseEntity<List<Product>> filterByMinStock(@RequestParam String category,
                                                          @RequestParam int minStock) {
        return ResponseEntity.ok(productService.findInStock(category, minStock));
    }

    /**
     * Updates mutable fields of an existing product.
     *
     * <pre>
     * PUT /api/products/{id}?category=Electronics
     * Body: { "price": 799.99, "stock": 45 }
     * </pre>
     *
     * <p>Only non-null fields in the body are applied — the rest remain unchanged.</p>
     *
     * @param id       UUID (clustering key) path variable
     * @param category partition key query parameter
     * @param request  partial update DTO
     * @return 200 OK with the updated Product, or 404 Not Found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable UUID id,
                                                 @RequestParam String category,
                                                 @Valid @RequestBody UpdateProductRequest request) {
        try {
            Product updated = productService.updateProduct(category, id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Deletes a product by its composite primary key.
     *
     * <pre>
     * DELETE /api/products/{id}?category=Electronics
     * </pre>
     *
     * @param id       UUID (clustering key) path variable
     * @param category partition key query parameter
     * @return 204 No Content on success, or 404 Not Found if product does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id,
                                              @RequestParam String category) {
        try {
            productService.deleteProduct(category, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Global handler for validation errors thrown by {@code @Valid}.
     * Returns 400 Bad Request with the error message.
     *
     * @param ex the IllegalArgumentException thrown by the service
     * @return 400 Bad Request with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
