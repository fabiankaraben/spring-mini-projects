package com.example.entitylifecycle.controller;

import com.example.entitylifecycle.dto.ProductRequest;
import com.example.entitylifecycle.dto.ProductResponse;
import com.example.entitylifecycle.service.ProductService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes CRUD endpoints for {@code Product} resources.
 *
 * <p>The controller's responsibility is deliberately thin: it handles HTTP
 * mapping, validates incoming requests, and delegates all business logic to
 * {@link ProductService}. The JPA lifecycle callbacks are completely transparent
 * at this layer — slug generation, timestamp stamping, and discount calculation
 * all happen inside the persistence layer.
 *
 * <p>Base path: {@code /api/products}
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection — single constructor; no {@code @Autowired} needed.
     *
     * @param productService the service handling product business logic
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * {@code GET /api/products} — list all products.
     *
     * <p>The {@code discountedPrice} field in each response is calculated
     * automatically by the {@code @PostLoad} callback — no manual calculation
     * is performed here.
     *
     * <p>Example:
     * <pre>
     * curl http://localhost:8080/api/products
     * </pre>
     *
     * @return 200 OK with list of products including their computed discounted prices
     */
    @GetMapping
    public List<ProductResponse> listAll() {
        return productService.findAll();
    }

    /**
     * {@code GET /api/products/discounted} — list all products that have a
     * non-zero discount.
     *
     * <p>Example:
     * <pre>
     * curl http://localhost:8080/api/products/discounted
     * </pre>
     *
     * @return 200 OK with list of discounted products
     */
    @GetMapping("/discounted")
    public List<ProductResponse> listDiscounted() {
        return productService.findDiscounted();
    }

    /**
     * {@code GET /api/products/{id}} — retrieve a single product by ID.
     *
     * <p>Example:
     * <pre>
     * curl http://localhost:8080/api/products/1
     * </pre>
     *
     * @param id the product's primary key
     * @return 200 OK with the product, or 404 if not found
     */
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.findById(id);
    }

    /**
     * {@code GET /api/products/slug/{slug}} — retrieve a product by its URL slug.
     *
     * <p>Slugs are generated automatically from the product name by the
     * {@code @PrePersist} callback, e.g. {@code "Wireless Headphones"} becomes
     * {@code "wireless-headphones"}.
     *
     * <p>Example:
     * <pre>
     * curl http://localhost:8080/api/products/slug/wireless-headphones
     * </pre>
     *
     * @param slug the URL slug to look up
     * @return 200 OK with the product, or 404 if not found
     */
    @GetMapping("/slug/{slug}")
    public ProductResponse getBySlug(@PathVariable String slug) {
        return productService.findBySlug(slug);
    }

    /**
     * {@code POST /api/products} — create a new product.
     *
     * <p>The response will contain:
     * <ul>
     *   <li>{@code slug}            — auto-generated from the product name by
     *       the {@code @PrePersist} callback.</li>
     *   <li>{@code createdAt}       — stamped by {@code @PrePersist}.</li>
     *   <li>{@code updatedAt}       — stamped by {@code @PrePersist}.</li>
     *   <li>{@code discountedPrice} — computed by {@code @PrePersist}.</li>
     * </ul>
     * None of these fields need to be supplied in the request body.
     *
     * <p>Example:
     * <pre>
     * curl -X POST http://localhost:8080/api/products \
     *      -H "Content-Type: application/json" \
     *      -d '{"name":"Wireless Headphones","description":"Great sound","price":99.99,"discountPercent":20}'
     * </pre>
     *
     * @param request validated inbound DTO
     * @return 201 Created with the persisted product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    /**
     * {@code PUT /api/products/{id}} — replace an existing product's data.
     *
     * <p>The {@code slug} is NOT updated even if the name changes — the original
     * slug is preserved to keep existing URLs stable (the column is declared
     * {@code updatable = false} at the database level).
     *
     * <p>Example:
     * <pre>
     * curl -X PUT http://localhost:8080/api/products/1 \
     *      -H "Content-Type: application/json" \
     *      -d '{"name":"Updated Headphones","description":"Even better","price":89.99,"discountPercent":10}'
     * </pre>
     *
     * @param id      the ID of the product to update
     * @param request validated inbound DTO with new values
     * @return 200 OK with the updated product
     */
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    /**
     * {@code DELETE /api/products/{id}} — remove a product.
     *
     * <p>After the DELETE, the external listener's {@code @PostRemove} callback
     * fires and writes an audit log entry.
     *
     * <p>Example:
     * <pre>
     * curl -X DELETE http://localhost:8080/api/products/1
     * </pre>
     *
     * @param id the ID of the product to delete
     * @return 204 No Content on success, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
