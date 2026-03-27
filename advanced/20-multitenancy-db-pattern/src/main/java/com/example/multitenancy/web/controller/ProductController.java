package com.example.multitenancy.web.controller;

import com.example.multitenancy.domain.Product;
import com.example.multitenancy.domain.ProductService;
import com.example.multitenancy.tenant.TenantContext;
import com.example.multitenancy.web.dto.ProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing the Products CRUD API.
 *
 * <p>All endpoints are tenant-scoped: the active tenant is resolved from the
 * {@code X-Tenant-ID} HTTP header by {@link com.example.multitenancy.web.interceptor.TenantInterceptor}
 * before any controller method is invoked. The tenant context flows transparently
 * through the service and repository layers to PostgreSQL via the Hibernate
 * multi-tenancy SPI.</p>
 *
 * <h2>Base path</h2>
 * <p>All endpoints are under {@code /api/products}.</p>
 *
 * <h2>Required header</h2>
 * <p>Every request must include the {@code X-Tenant-ID} header (e.g.,
 * {@code X-Tenant-ID: tenant_alpha}). Requests without this header will receive
 * a 400 Bad Request response.</p>
 *
 * <h2>Tenant isolation proof</h2>
 * <p>A product created via one tenant ID is completely invisible when the same
 * endpoint is called with a different tenant ID. This is the core value of the
 * schema-per-tenant strategy.</p>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructs the controller with its service dependency.
     *
     * @param productService the product business logic service
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Lists all products for the current tenant, with optional keyword search.
     *
     * <p>Returns HTTP 400 if the {@code X-Tenant-ID} header is missing.</p>
     *
     * <p>Example: {@code GET /api/products} returns all products for the tenant.
     * {@code GET /api/products?search=laptop} returns only products with "laptop"
     * in the name.</p>
     *
     * @param search optional keyword to filter products by name (case-insensitive)
     * @return 200 OK with the list of products; 400 if no tenant header
     */
    @GetMapping
    public ResponseEntity<List<Product>> list(
            @RequestParam(required = false) String search) {

        // Guard: every products endpoint requires a resolved tenant
        if (TenantContext.getCurrentTenant() == null) {
            return ResponseEntity.badRequest().build();
        }

        List<Product> products = (search != null && !search.isBlank())
                ? productService.search(search)
                : productService.findAll();

        return ResponseEntity.ok(products);
    }

    /**
     * Retrieves a single product by its ID within the current tenant's schema.
     *
     * <p>Returns HTTP 404 if the product does not exist in the current tenant's
     * schema (even if a product with the same ID exists in another tenant's schema).</p>
     *
     * @param id the product's primary key
     * @return 200 OK with the product; 404 if not found; 400 if no tenant header
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        if (TenantContext.getCurrentTenant() == null) {
            return ResponseEntity.badRequest().build();
        }
        return productService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new product in the current tenant's schema.
     *
     * <p>The request body is validated by Bean Validation ({@code @Valid}). If
     * any constraint fails (e.g., blank name, negative price), Spring returns HTTP
     * 400 with a structured error body before this method is called.</p>
     *
     * @param request the new product's data (validated)
     * @return 201 Created with the persisted product (including its generated ID)
     */
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
        if (TenantContext.getCurrentTenant() == null) {
            return ResponseEntity.badRequest().build();
        }
        Product created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing product in the current tenant's schema.
     *
     * <p>The entire product is replaced with the provided data (PUT semantics).
     * Only products visible in the current tenant's schema can be updated.</p>
     *
     * @param id      the ID of the product to update
     * @param request the replacement product data (validated)
     * @return 200 OK with the updated product; 404 if not found; 400 if invalid input
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        if (TenantContext.getCurrentTenant() == null) {
            return ResponseEntity.badRequest().build();
        }
        return productService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a product by its ID from the current tenant's schema.
     *
     * <p>Returns HTTP 204 No Content on success, or 404 if the product does not
     * exist in the current tenant's schema.</p>
     *
     * @param id the ID of the product to delete
     * @return 204 No Content on success; 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (TenantContext.getCurrentTenant() == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean deleted = productService.delete(id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
