package com.example.circuitbreaker.controller;

import com.example.circuitbreaker.domain.Product;
import com.example.circuitbreaker.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes product endpoints protected by a circuit breaker.
 *
 * <p>This controller is intentionally thin: it handles only HTTP concerns
 * (path variables, response status, JSON serialisation). All fault-tolerance
 * logic (circuit breaker, retry, fallback) lives in {@link ProductService}.
 *
 * <p>API routes:
 * <pre>
 *   GET /api/products          – list all products
 *   GET /api/products/{id}     – single product by ID
 * </pre>
 *
 * <p><strong>Fallback behaviour:</strong>
 * When the circuit is OPEN or the upstream service fails after retries, the
 * service layer returns a degraded response (fallback product / empty list)
 * and this controller returns it with HTTP 200. Clients should check the
 * {@code available} field on a single-product response, or check the
 * circuit breaker status endpoint to detect degraded mode.
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection ensures the dependency is explicit and testable.
     *
     * @param productService service that applies Resilience4j fault tolerance
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * List all products from the upstream inventory service.
     *
     * <p>Returns HTTP 200 in all cases (including fallback): the body will be
     * an empty JSON array {@code []} when the circuit is OPEN.
     *
     * @return 200 OK with a JSON array of products (may be empty in degraded mode)
     */
    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * Fetch a single product by its ID.
     *
     * <p>Returns HTTP 200 in all cases. When the circuit is OPEN or the upstream
     * fails, the response body contains a fallback product with:
     * <ul>
     *   <li>{@code name} → "Product Unavailable"</li>
     *   <li>{@code available} → {@code false}</li>
     *   <li>{@code price} → {@code 0}</li>
     * </ul>
     *
     * @param id the product identifier in the URL path
     * @return 200 OK with the product JSON (real or fallback)
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable("id") Integer id) {
        Product product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }
}
