package com.example.basiccaching.controller;

import com.example.basiccaching.model.Product;
import com.example.basiccaching.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing CRUD endpoints for {@link Product}.
 *
 * <p>
 * This controller delegates all business logic (including caching) to
 * {@link ProductService}. The controller itself is unaware of caching — this is
 * a key advantage of Spring's cache abstraction: caching is a cross-cutting
 * concern
 * applied transparently at the service layer.
 * </p>
 *
 * <p>
 * Base path: {@code /api/products}
 * </p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    // Constructor injection (preferred over @Autowired field injection)
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/products
     *
     * <p>
     * Returns all products. The first call hits the service's real logic
     * (with the simulated delay). Subsequent calls are served from cache instantly.
     * </p>
     *
     * @return list of all products with HTTP 200
     */
    @GetMapping
    public List<Product> getAllProducts() {
        return productService.findAll();
    }

    /**
     * GET /api/products/{id}
     *
     * <p>
     * Returns a single product by ID. Cached per ID. Returns HTTP 404 if not found.
     * </p>
     *
     * @param id the product ID (path variable)
     * @return the product or 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.findById(id)
                // Wrap in 200 OK if found
                .map(ResponseEntity::ok)
                // Return 404 Not Found if not present
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/products
     *
     * <p>
     * Creates a new product. The service will also update the cache entry for
     * this product and evict the "all" list cache.
     * </p>
     *
     * @param product the product data from the request body (JSON)
     * @return the created product with HTTP 201
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        Product saved = productService.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * PUT /api/products/{id}
     *
     * <p>
     * Updates an existing product. We reconstruct a Product record with the given
     * ID and save it. This triggers {@code @CachePut} to refresh the cache entry.
     * </p>
     *
     * @param id      the product ID from the path
     * @param product the updated product data from the request body
     * @return the updated product with HTTP 200, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id,
            @RequestBody Product product) {
        // Check that the product exists before updating
        return productService.findById(id)
                .map(existing -> {
                    // Build a new record with the path-provided ID (ignores any id in body)
                    Product updated = new Product(id, product.name(), product.category(), product.price());
                    Product saved = productService.save(updated);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/products/{id}
     *
     * <p>
     * Deletes a product and evicts it from the cache. Returns HTTP 204 on success
     * or HTTP 404 if the product does not exist.
     * </p>
     *
     * @param id the product ID to delete
     * @return HTTP 204 No Content, or 404
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        boolean deleted = productService.deleteById(id);
        if (deleted) {
            return ResponseEntity.noContent().build(); // 204
        }
        return ResponseEntity.notFound().build(); // 404
    }
}
