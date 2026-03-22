package com.example.resourceserver.controller;

import com.example.resourceserver.domain.Product;
import com.example.resourceserver.dto.CreateProductRequest;
import com.example.resourceserver.dto.UpdateProductRequest;
import com.example.resourceserver.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Products API.
 *
 * <p>This controller exposes a CRUD API for products. All endpoints require a valid
 * JWT Bearer token. The required scope (read vs. write) is enforced at two layers:
 * <ol>
 *   <li><b>HTTP layer</b> — in {@code ResourceServerSecurityConfig}, which checks
 *       authorities before the request reaches this controller.</li>
 *   <li><b>Method layer</b> — via {@code @PreAuthorize} annotations on individual
 *       methods for additional fine-grained control.</li>
 * </ol>
 *
 * <p><b>Endpoints summary:</b>
 * <ul>
 *   <li>{@code GET  /api/products}          — list all products (scope: products.read)</li>
 *   <li>{@code GET  /api/products/{id}}     — get product by ID (scope: products.read)</li>
 *   <li>{@code GET  /api/products?category} — filter by category (scope: products.read)</li>
 *   <li>{@code POST /api/products}          — create product (scope: products.write)</li>
 *   <li>{@code PUT  /api/products/{id}}     — update product (scope: products.write)</li>
 *   <li>{@code DELETE /api/products/{id}}  — delete product (scope: products.write)</li>
 * </ul>
 *
 * <p><b>JWT principal extraction:</b>
 * The {@code @AuthenticationPrincipal Jwt jwt} parameter in several methods injects
 * the validated JWT object directly into the handler method. This provides access to
 * all JWT claims (subject, scopes, roles, tenant, etc.) without manual SecurityContext
 * lookups. It is useful for audit logging and claim-based business logic.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /**
     * The service that implements product business logic.
     * Constructor injection is preferred over field injection.
     */
    private final ProductService productService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param productService the service that handles product CRUD operations
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Lists all products, or filters by category if the {@code category} query
     * parameter is provided.
     *
     * <p>This endpoint requires the {@code SCOPE_products.read} authority, which
     * corresponds to a JWT with {@code "scope": "products.read"}.
     *
     * <p>The {@code @AuthenticationPrincipal Jwt jwt} parameter injects the full
     * validated JWT so we can log which client made the request. In a real system
     * you would use a proper audit logging framework.
     *
     * @param category optional query parameter for category filtering
     * @param jwt      the validated JWT of the authenticated caller (injected by Spring)
     * @return HTTP 200 with the list of products (may be empty)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_products.read')")
    public ResponseEntity<List<Product>> listProducts(
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal Jwt jwt) {

        // Log the client making the request (useful for audit trails)
        // jwt.getSubject() returns the "sub" claim — the client or user identifier
        String caller = jwt != null ? jwt.getSubject() : "unknown";

        List<Product> products;
        if (category != null && !category.isBlank()) {
            // Filter by category if the query parameter is provided
            products = productService.getProductsByCategory(category);
        } else {
            // Return all products
            products = productService.getAllProducts();
        }

        // Always return 200 OK even if the list is empty —
        // an empty list is a valid response, not a "not found" error.
        return ResponseEntity.ok(products);
    }

    /**
     * Retrieves a single product by its unique ID.
     *
     * <p>Returns HTTP 404 Not Found if no product with the given ID exists.
     *
     * @param id  the product ID from the URL path variable
     * @param jwt the validated JWT of the authenticated caller
     * @return HTTP 200 with the product body, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_products.read')")
    public ResponseEntity<Product> getProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        return productService.getProductById(id)
                // Map to 200 OK response if product is found
                .map(ResponseEntity::ok)
                // Return 404 Not Found if no product has this ID
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new product.
     *
     * <p>The request body is validated by Bean Validation (triggered by {@code @Valid}).
     * If validation fails, Spring returns HTTP 400 Bad Request automatically with
     * details about the failing constraints.
     *
     * <p>Returns HTTP 201 Created with the newly created product in the response body.
     * In a complete REST API, the response would also include a {@code Location} header
     * pointing to the new resource URL.
     *
     * <p>This endpoint requires the {@code SCOPE_products.write} authority. The
     * {@code @PreAuthorize} here provides method-level defense-in-depth beyond the
     * HTTP-layer check in the security configuration.
     *
     * @param request the create request body (validated)
     * @param jwt     the validated JWT of the authenticated caller
     * @return HTTP 201 Created with the created product
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_products.write')")
    public ResponseEntity<Product> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Product created = productService.createProduct(request);

        // HTTP 201 Created is the correct status for successful resource creation
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing product.
     *
     * <p>Only fields provided in the request body are updated (partial update semantics).
     * Fields that are {@code null} in the request body are ignored.
     *
     * @param id      the ID of the product to update
     * @param request the update request body (validated)
     * @param jwt     the validated JWT of the authenticated caller
     * @return HTTP 200 with the updated product, or HTTP 404 if not found
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_products.write')")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return productService.updateProduct(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a product by its unique ID.
     *
     * <p>Returns HTTP 204 No Content on successful deletion (REST convention: no body
     * in the response for delete operations). Returns HTTP 404 if the product does not exist.
     *
     * @param id  the ID of the product to delete
     * @param jwt the validated JWT of the authenticated caller
     * @return HTTP 204 No Content if deleted, or HTTP 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_products.write')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        boolean deleted = productService.deleteProduct(id);

        if (deleted) {
            // HTTP 204 No Content: success, but nothing to return in the body
            return ResponseEntity.noContent().build();
        } else {
            // HTTP 404 Not Found: no product had this ID
            return ResponseEntity.notFound().build();
        }
    }
}
