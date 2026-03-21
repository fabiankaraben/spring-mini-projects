package com.example.tracing.controller;

import com.example.tracing.model.Product;
import com.example.tracing.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * REST controller for product catalogue operations.
 *
 * <p>Exposes two endpoints:
 * <ul>
 *   <li>{@code GET /products} — list all products in the catalogue.</li>
 *   <li>{@code GET /products/{id}} — look up a single product and observe
 *       how custom span tags (productId, name, category) appear in Zipkin.</li>
 * </ul>
 *
 * <p><b>How tracing works here:</b>
 * Spring Boot auto-configures an {@code ObservationFilter} (via
 * {@code micrometer-tracing-bridge-brave}) that creates an HTTP server span for
 * every incoming request automatically. This controller does not need to interact
 * with the {@code Tracer} API directly — that work is done inside
 * {@link ProductService} to show how to enrich the auto-created span from
 * the service layer.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Lists all products in the catalogue.
     *
     * <p>Each product in the response includes the {@code traceId} and
     * {@code spanId} active at the time the service layer ran.
     *
     * @return HTTP 200 with a JSON array of all products
     */
    @GetMapping
    public Collection<Product> listProducts() {
        log.info("Listing all products");
        return productService.findAll();
    }

    /**
     * Looks up a single product by its identifier.
     *
     * <p>The {@link ProductService} enriches the active span with product-specific
     * tags ({@code product.id}, {@code product.name}, {@code product.category}).
     * These tags are visible in Zipkin's span detail panel after the request completes.
     *
     * @param id the product identifier (e.g., "PROD-001")
     * @return HTTP 200 with the product body, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id) {
        log.info("Looking up product [id={}]", id);
        Product product = productService.findById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }
}
