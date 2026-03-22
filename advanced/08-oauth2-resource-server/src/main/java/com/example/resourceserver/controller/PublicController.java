package com.example.resourceserver.controller;

import com.example.resourceserver.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for publicly accessible (unauthenticated) endpoints.
 *
 * <p>These endpoints are accessible without any OAuth2 token. They demonstrate
 * the contrast with the protected {@link ProductController} endpoints and are
 * useful for:
 * <ul>
 *   <li>Health checking from load balancers and orchestration systems.</li>
 *   <li>Exposing server metadata to API consumers before they obtain a token.</li>
 * </ul>
 *
 * <p>The {@code /api/public/**} path is explicitly permitted in
 * {@link com.example.resourceserver.config.ResourceServerSecurityConfig} without
 * authentication.
 *
 * <p><b>Available endpoints:</b>
 * <ul>
 *   <li>{@code GET /api/public/info} — returns server metadata (version, available scopes)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    /**
     * Service injected to provide summary statistics for the info endpoint.
     * This demonstrates that even public controllers can use domain services.
     */
    private final ProductService productService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param productService the product service (used for product count statistics)
     */
    public PublicController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Returns publicly accessible server information.
     *
     * <p>This endpoint provides API consumers with metadata about the resource server:
     * what it does, what OAuth2 scopes are available, and what token format is expected.
     * This information is useful for API clients discovering the resource server for
     * the first time.
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "service": "OAuth2 Resource Server — Products API",
     *   "version": "1.0.0",
     *   "timestamp": "2025-01-01T00:00:00Z",
     *   "productCount": 6,
     *   "requiredTokenType": "Bearer JWT",
     *   "availableScopes": {
     *     "products.read": "Read access to the product catalog",
     *     "products.write": "Create, update, and delete products"
     *   },
     *   "protectedEndpoints": {
     *     "GET /api/products": "products.read",
     *     "GET /api/products/{id}": "products.read",
     *     "POST /api/products": "products.write",
     *     "PUT /api/products/{id}": "products.write",
     *     "DELETE /api/products/{id}": "products.write"
     *   }
     * }
     * }</pre>
     *
     * @return HTTP 200 with server information JSON
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        // LinkedHashMap preserves insertion order for a consistent JSON output
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("service", "OAuth2 Resource Server — Products API");
        info.put("version", "1.0.0");
        // Current server timestamp in ISO-8601 UTC format
        info.put("timestamp", Instant.now().toString());
        // Live product count from the service layer
        info.put("productCount", productService.getProductCount());
        info.put("requiredTokenType", "Bearer JWT");

        // Describe the available OAuth2 scopes so clients know what to request
        Map<String, String> scopes = new LinkedHashMap<>();
        scopes.put("products.read", "Read access to the product catalog");
        scopes.put("products.write", "Create, update, and delete products");
        info.put("availableScopes", scopes);

        // Map each endpoint to the required scope for quick reference
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("GET /api/products", "products.read");
        endpoints.put("GET /api/products/{id}", "products.read");
        endpoints.put("POST /api/products", "products.write");
        endpoints.put("PUT /api/products/{id}", "products.write");
        endpoints.put("DELETE /api/products/{id}", "products.write");
        info.put("protectedEndpoints", endpoints);

        return ResponseEntity.ok(info);
    }
}
