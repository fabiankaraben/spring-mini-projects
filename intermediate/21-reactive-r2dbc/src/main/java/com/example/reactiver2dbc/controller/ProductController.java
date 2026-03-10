package com.example.reactiver2dbc.controller;

import com.example.reactiver2dbc.domain.Product;
import com.example.reactiver2dbc.dto.ProductRequest;
import com.example.reactiver2dbc.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * REST controller exposing non-blocking HTTP endpoints for product management.
 *
 * <p>Annotations:
 * <ul>
 *   <li>{@code @RestController} – combines {@code @Controller} and {@code @ResponseBody}.
 *       Every return value is automatically serialised to JSON by Jackson.</li>
 *   <li>{@code @RequestMapping("/api/products")} – all endpoints in this class are
 *       prefixed with {@code /api/products}.</li>
 * </ul>
 *
 * <p><strong>How WebFlux handles reactive return types:</strong><br>
 * Spring WebFlux subscribes to the returned {@link Mono} or {@link Flux} and writes
 * the emitted items to the HTTP response asynchronously. The calling thread is never
 * blocked waiting for the database — it handles other requests while the SQL I/O completes.
 *
 * <p><strong>Controller responsibilities:</strong>
 * <ul>
 *   <li>Parse and validate HTTP request data ({@code @Valid}, {@code @PathVariable}, etc.).</li>
 *   <li>Delegate all business logic to {@link ProductService}.</li>
 *   <li>Map empty Monos to HTTP 404 via {@code switchIfEmpty}.</li>
 *   <li>Set appropriate HTTP status codes ({@code @ResponseStatus}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection — makes the dependency explicit and testable.
     *
     * @param productService the service containing product business logic
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ── GET /api/products ─────────────────────────────────────────────────────────

    /**
     * List all products.
     *
     * <p>Returns a {@link Flux} that streams each product as JSON. WebFlux writes each
     * row to the response body as it arrives from PostgreSQL via R2DBC — no need to
     * buffer all rows in memory.
     *
     * @return a Flux emitting all products
     */
    @GetMapping
    public Flux<Product> getAllProducts() {
        return productService.findAll();
    }

    // ── GET /api/products/{id} ────────────────────────────────────────────────────

    /**
     * Get a single product by its primary key.
     *
     * <p>{@code switchIfEmpty} transforms an empty {@link Mono} (product not found)
     * into a {@link Mono} that emits an error, which WebFlux converts to HTTP 404.
     *
     * @param id the product's primary key
     * @return a Mono emitting the product, or 404 if not found
     */
    @GetMapping("/{id}")
    public Mono<Product> getProductById(@PathVariable Long id) {
        return productService.findById(id)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)));
    }

    // ── GET /api/products/category/{category} ─────────────────────────────────────

    /**
     * List all products in a given category.
     *
     * @param category the category to filter by (path variable)
     * @return a Flux emitting products in the specified category
     */
    @GetMapping("/category/{category}")
    public Flux<Product> getByCategory(@PathVariable String category) {
        return productService.findByCategory(category);
    }

    // ── GET /api/products/active ───────────────────────────────────────────────────

    /**
     * List all active products (visible on the storefront).
     *
     * @return a Flux emitting only active products
     */
    @GetMapping("/active")
    public Flux<Product> getActive() {
        return productService.findActive();
    }

    // ── GET /api/products/active/category/{category} ──────────────────────────────

    /**
     * List all active products within a specific category.
     *
     * @param category the category to filter by
     * @return a Flux emitting active products in the given category
     */
    @GetMapping("/active/category/{category}")
    public Flux<Product> getActiveByCategoryAndActive(@PathVariable String category) {
        return productService.findActiveByCategoryAndActive(category);
    }

    // ── GET /api/products/search?keyword=... ──────────────────────────────────────

    /**
     * Search products by keyword in their name (case-insensitive substring match).
     *
     * <p>The {@code keyword} query parameter is required — Spring returns 400 if missing.
     *
     * @param keyword text to search for within product names
     * @return a Flux emitting matching products
     */
    @GetMapping("/search")
    public Flux<Product> searchByName(@RequestParam String keyword) {
        return productService.searchByName(keyword);
    }

    // ── GET /api/products/price-range?min=...&max=... ─────────────────────────────

    /**
     * List all products within a price range.
     *
     * <p>Both {@code min} and {@code max} are required query parameters.
     * Results are ordered by price ascending.
     *
     * @param min minimum price (inclusive)
     * @param max maximum price (inclusive)
     * @return a Flux emitting products in the price range
     */
    @GetMapping("/price-range")
    public Flux<Product> getByPriceRange(@RequestParam BigDecimal min,
                                         @RequestParam BigDecimal max) {
        return productService.findByPriceRange(min, max);
    }

    // ── GET /api/products/low-stock?threshold=... ─────────────────────────────────

    /**
     * List products with stock quantity at or below the given threshold.
     *
     * <p>Defaults to threshold=5 if not specified, which covers "almost out of stock".
     *
     * @param threshold maximum stock quantity to include (defaults to 5)
     * @return a Flux emitting low-stock products
     */
    @GetMapping("/low-stock")
    public Flux<Product> getLowStock(@RequestParam(defaultValue = "5") int threshold) {
        return productService.findLowStock(threshold);
    }

    // ── GET /api/products/category/{category}/count ───────────────────────────────

    /**
     * Count how many products exist in a given category.
     *
     * <p>Returns a {@link Mono}{@code <Long>} serialised as a plain JSON number.
     *
     * @param category the category to count products for
     * @return a Mono emitting the count
     */
    @GetMapping("/category/{category}/count")
    public Mono<Long> countByCategory(@PathVariable String category) {
        return productService.countByCategory(category);
    }

    // ── POST /api/products ────────────────────────────────────────────────────────

    /**
     * Create a new product.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body. If any constraint
     * is violated, Spring WebFlux returns HTTP 400 Bad Request before this method is invoked.
     *
     * <p>{@code @ResponseStatus(CREATED)} changes the default 200 status to 201.
     *
     * @param request the product data from the request body
     * @return a Mono emitting the created product with its generated id
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    // ── PUT /api/products/{id} ────────────────────────────────────────────────────

    /**
     * Update an existing product (full replacement — PUT semantics).
     *
     * <p>If the product is not found, the service returns an empty {@link Mono}.
     * {@code switchIfEmpty} converts this to a 404 error.
     *
     * @param id      the product's primary key
     * @param request the new field values
     * @return a Mono emitting the updated product, or 404 if not found
     */
    @PutMapping("/{id}")
    public Mono<Product> updateProduct(@PathVariable Long id,
                                       @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id)));
    }

    // ── DELETE /api/products/{id} ─────────────────────────────────────────────────

    /**
     * Delete a product by its primary key.
     *
     * <p>The service returns {@code Mono<Boolean>}:
     * <ul>
     *   <li>{@code true} — product existed and was deleted → HTTP 204 No Content.</li>
     *   <li>{@code false} — product was not found → HTTP 404 Not Found.</li>
     * </ul>
     *
     * @param id the product's primary key
     * @return an empty Mono (HTTP 204) or a 404 error
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteProduct(@PathVariable Long id) {
        return productService.deleteById(id)
                .flatMap(deleted -> {
                    if (deleted) {
                        // Product was deleted — return empty Mono (HTTP 204 No Content)
                        return Mono.<Void>empty();
                    } else {
                        // Product was not found — signal HTTP 404
                        return Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
                    }
                });
    }
}
