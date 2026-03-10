package com.example.softdelete.controller;

import com.example.softdelete.domain.Product;
import com.example.softdelete.dto.ProductRequest;
import com.example.softdelete.dto.ProductResponse;
import com.example.softdelete.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing CRUD endpoints for {@link Product} entities.
 *
 * <h2>Soft-delete endpoints</h2>
 * <ul>
 *   <li>{@code DELETE /api/products/{id}} – soft-deletes the product (sets deleted=true).</li>
 *   <li>{@code POST   /api/products/{id}/restore} – restores a previously soft-deleted product.</li>
 *   <li>{@code GET    /api/products/deleted} – lists all soft-deleted products (admin view).</li>
 * </ul>
 *
 * <h2>URL design</h2>
 * <p>All endpoints are under {@code /api/products}.  The {@code /deleted} sub-resource and
 * {@code /restore} action are deliberately separate from the main resource path to make the
 * exceptional nature of those operations explicit.</p>
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

    // ── Normal CRUD endpoints ──────────────────────────────────────────────────

    /**
     * Lists all active (non-deleted) products.
     *
     * <p>Soft-deleted products are <em>not</em> included here thanks to the
     * {@code @SQLRestriction} on the {@link Product} entity.</p>
     *
     * @return 200 OK with the list of active products
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
     * Returns a single active product by its ID.
     *
     * @param id the product's primary key
     * @return 200 OK with the product, or 404 if not found / already soft-deleted
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        Product product = productService.findById(id);
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    /**
     * Searches active products by a case-insensitive name fragment.
     *
     * <p>Example: {@code GET /api/products/search?name=phone} returns all active
     * products whose name contains "phone" (ignoring case).</p>
     *
     * @param name the substring to search for
     * @return 200 OK with the matching active products
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
     * Filters active products by category.
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

    /**
     * Creates a new product.
     *
     * @param request validated product data (name, description, price, category)
     * @return 201 Created with the persisted product including generated ID and timestamps
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    /**
     * Updates an existing active product.
     *
     * <p>Only active (non-deleted) products can be updated.  Attempting to update
     * a soft-deleted product returns 404.</p>
     *
     * @param id      the product's primary key
     * @param request new field values (name, description, price, category)
     * @return 200 OK with the updated product
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        Product updated = productService.update(id, request);
        return ResponseEntity.ok(ProductResponse.from(updated));
    }

    /**
     * Soft-deletes a product by setting its {@code deleted} flag to {@code true}.
     *
     * <p>The row is <em>NOT</em> removed from the database.  The product becomes
     * invisible to all normal (non-native) queries but can be seen via
     * {@link #listDeleted()} and restored via {@link #restore(Long)}.</p>
     *
     * @param id the product's primary key
     * @return 204 No Content on success, or 404 if the product is not found / already deleted
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Soft-delete specific endpoints ─────────────────────────────────────────

    /**
     * Lists all soft-deleted products (admin / audit view).
     *
     * <p>This endpoint bypasses the {@code @SQLRestriction} filter via a native SQL
     * query so that deleted products – which are normally invisible – can be
     * inspected and potentially restored.</p>
     *
     * @return 200 OK with all soft-deleted products
     */
    @GetMapping("/deleted")
    public ResponseEntity<List<ProductResponse>> listDeleted() {
        List<ProductResponse> products = productService.findAllDeleted()
                .stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    /**
     * Restores a previously soft-deleted product, making it active again.
     *
     * <p>After a successful restore the product will appear in all normal query
     * results (e.g. {@link #listAll()}) as if it had never been deleted.</p>
     *
     * @param id the product's primary key
     * @return 200 OK with the restored product, or 404 if no deleted product with that ID exists
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<ProductResponse> restore(@PathVariable Long id) {
        Product restored = productService.restore(id);
        return ResponseEntity.ok(ProductResponse.from(restored));
    }

    /**
     * Returns statistics about soft-deleted products (admin view).
     *
     * @return 200 OK with a simple count of deleted records
     */
    @GetMapping("/deleted/count")
    public ResponseEntity<Long> countDeleted() {
        return ResponseEntity.ok(productService.countDeleted());
    }
}
