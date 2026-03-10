package com.example.optimisticlocking.controller;

import com.example.optimisticlocking.domain.Product;
import com.example.optimisticlocking.dto.ProductRequest;
import com.example.optimisticlocking.dto.ProductResponse;
import com.example.optimisticlocking.dto.ProductUpdateRequest;
import com.example.optimisticlocking.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing CRUD endpoints for {@link Product} entities.
 *
 * <h2>Optimistic Locking endpoints</h2>
 * <p>The key endpoint that demonstrates optimistic locking is
 * {@code PUT /api/products/{id}}.  It requires the client to supply the current
 * {@code version} value in the request body.  If the version is stale (because
 * another request updated the product in the meantime), the service throws
 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} which
 * the {@link com.example.optimisticlocking.exception.GlobalExceptionHandler}
 * translates into an HTTP 409 Conflict response.</p>
 *
 * <h2>URL design</h2>
 * <p>All endpoints are under {@code /api/products}.  Two separate request DTOs are
 * used: {@link ProductRequest} for creation (no version needed) and
 * {@link ProductUpdateRequest} for updates (version required).</p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection – avoids field injection for easier testability.
     *
     * @param productService service handling product business logic
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ── Read endpoints ─────────────────────────────────────────────────────────

    /**
     * Lists all products.
     *
     * @return 200 OK with the list of all products
     */
    @GetMapping
    public ResponseEntity<List<ProductResponse>> listAll() {
        List<ProductResponse> products = productService.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    /**
     * Returns a single product by its ID.
     *
     * <p>The response includes the current {@code version} field, which the client
     * must echo back in any subsequent PUT request to prove it is working with
     * up-to-date data.</p>
     *
     * @param id the product's primary key
     * @return 200 OK with the product, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        Product product = productService.findById(id);
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    /**
     * Searches products by a case-insensitive name fragment.
     *
     * @param name the substring to search for
     * @return 200 OK with the matching products
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchByName(
            @RequestParam(defaultValue = "") String name) {
        List<ProductResponse> products = productService.searchByName(name)
                .stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    /**
     * Filters products by category.
     *
     * @param category the category to filter by
     * @return 200 OK with products in the given category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductResponse>> getByCategory(@PathVariable String category) {
        List<ProductResponse> products = productService.findByCategory(category)
                .stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    // ── Write endpoints ────────────────────────────────────────────────────────

    /**
     * Creates a new product.
     *
     * <p>The response will include {@code version: 0}, which Hibernate sets automatically
     * on the first INSERT.  Clients should store this value and include it in future
     * PUT requests for this resource.</p>
     *
     * @param request validated product data (name, description, price, stock, category)
     * @return 201 Created with the persisted product including generated ID and version=0
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    /**
     * Updates an existing product, enforcing optimistic locking.
     *
     * <h2>How optimistic locking works here</h2>
     * <ol>
     *   <li>Client fetches the product with GET – receives response including current {@code version}.</li>
     *   <li>Client modifies the desired fields locally.</li>
     *   <li>Client sends PUT with all fields including the same {@code version} it received.</li>
     *   <li>If another client updated the product between step 1 and step 3, the stored
     *       version in the database is now higher than what this client sent.</li>
     *   <li>Hibernate detects the mismatch → throws
     *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     *       → this endpoint returns <strong>409 Conflict</strong>.</li>
     *   <li>On success: the response contains the product with an incremented version.</li>
     * </ol>
     *
     * @param id      the product's primary key
     * @param request updated field values plus the current version (required)
     * @return 200 OK with the updated product (new version), or 404/409
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        Product updated = productService.update(id, request);
        return ResponseEntity.ok(ProductResponse.from(updated));
    }

    /**
     * Deletes a product by its ID.
     *
     * @param id the product's primary key
     * @return 204 No Content on success, or 404 if the product is not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
