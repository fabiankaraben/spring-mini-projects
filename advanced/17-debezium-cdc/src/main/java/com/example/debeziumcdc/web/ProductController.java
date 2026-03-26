package com.example.debeziumcdc.web;

import com.example.debeziumcdc.domain.Product;
import com.example.debeziumcdc.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * REST controller for the product catalogue API.
 *
 * <p>All endpoints delegate to {@link ProductService}, which persists changes
 * to PostgreSQL. Every committed change is automatically captured by Debezium
 * and streamed to the {@code product-cdc-events} Kafka topic.
 *
 * <p>API endpoints:
 * <ul>
 *   <li>{@code GET  /api/products}         — list all products</li>
 *   <li>{@code GET  /api/products/{id}}     — get a product by ID</li>
 *   <li>{@code POST /api/products}          — create a new product (triggers CDC INSERT)</li>
 *   <li>{@code PUT  /api/products/{id}}     — update a product (triggers CDC UPDATE)</li>
 *   <li>{@code DELETE /api/products/{id}}   — delete a product (triggers CDC DELETE)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // -------------------------------------------------------------------------
    // GET /api/products
    // -------------------------------------------------------------------------

    /**
     * Returns the full product catalogue.
     *
     * @return 200 OK with a JSON array of all products
     */
    @GetMapping
    public ResponseEntity<List<Product>> listProducts() {
        List<Product> products = productService.findAll();
        return ResponseEntity.ok(products);
    }

    // -------------------------------------------------------------------------
    // GET /api/products/{id}
    // -------------------------------------------------------------------------

    /**
     * Returns a single product by its primary key.
     *
     * @param id the product database ID
     * @return 200 OK with the product body, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // POST /api/products
    // -------------------------------------------------------------------------

    /**
     * Creates a new product.
     *
     * <p>After the transaction commits, Debezium captures the INSERT from the
     * PostgreSQL WAL and emits a CREATE event to the Kafka topic.
     *
     * @param request validated request body
     * @return 201 Created with the new product body and a Location header
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product created = productService.createProduct(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getStock());

        // Build the Location header: /api/products/{newId}
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        log.info("Product created via REST: id={}", created.getId());
        return ResponseEntity.created(location).body(created);
    }

    // -------------------------------------------------------------------------
    // PUT /api/products/{id}
    // -------------------------------------------------------------------------

    /**
     * Updates an existing product (partial update — null fields are ignored).
     *
     * <p>After the transaction commits, Debezium captures the UPDATE from the
     * PostgreSQL WAL and emits an UPDATE event with before/after snapshots.
     *
     * @param id      the product ID to update
     * @param request the fields to change (null fields are left unchanged)
     * @return 200 OK with the updated product, or 404 Not Found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateProductRequest request) {
        try {
            Product updated = productService.updateProduct(
                    id,
                    request.getName(),
                    request.getDescription(),
                    request.getPrice(),
                    request.getStock());
            log.info("Product updated via REST: id={}", id);
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/products/{id}
    // -------------------------------------------------------------------------

    /**
     * Deletes a product by its ID.
     *
     * <p>After the transaction commits, Debezium captures the DELETE from the
     * PostgreSQL WAL and emits a DELETE event containing the before snapshot
     * (the values of the deleted row).
     *
     * @param id the product ID to delete
     * @return 204 No Content on success, or 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            log.info("Product deleted via REST: id={}", id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
