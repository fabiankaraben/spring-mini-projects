package com.example.testcontainerspostgres.controller;

import com.example.testcontainerspostgres.dto.ProductRequest;
import com.example.testcontainerspostgres.dto.ProductResponse;
import com.example.testcontainerspostgres.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller that exposes the Product API endpoints.
 *
 * <p>{@code @RestController} combines {@code @Controller} and {@code @ResponseBody}:
 * every method return value is automatically serialised to JSON.
 *
 * <p>{@code @RequestMapping("/api/products")} is the base path for all endpoints
 * defined in this class.
 *
 * <p>The controller is intentionally thin — it only:
 * <ol>
 *   <li>Receives and validates the HTTP request (via {@code @Valid}).</li>
 *   <li>Delegates to {@link ProductService} for business logic.</li>
 *   <li>Returns the appropriate HTTP response.</li>
 * </ol>
 * No business logic or database access happens here.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor injection. Spring automatically detects the single constructor
     * and injects the required bean — no {@code @Autowired} annotation needed.
     *
     * @param productService the service that handles product business logic
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // =========================================================================
    // GET endpoints
    // =========================================================================

    /**
     * Lists all products.
     *
     * <p>HTTP: {@code GET /api/products}
     *
     * @return 200 OK with the full list of products; empty array if none exist
     */
    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll();
    }

    /**
     * Retrieves a single product by its ID.
     *
     * <p>HTTP: {@code GET /api/products/{id}}
     *
     * @param id the product ID from the URL path
     * @return 200 OK with the product; 404 Not Found if the ID does not exist
     */
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    /**
     * Searches for products whose name contains the given keyword (case-insensitive).
     *
     * <p>HTTP: {@code GET /api/products/search?keyword=phone}
     *
     * @param keyword the search term (query parameter)
     * @return 200 OK with matching products; empty array if none match
     */
    @GetMapping("/search")
    public List<ProductResponse> search(@RequestParam String keyword) {
        return productService.search(keyword);
    }

    /**
     * Lists all products that are currently in stock (stockQuantity &gt; 0).
     *
     * <p>HTTP: {@code GET /api/products/in-stock}
     *
     * @return 200 OK with in-stock products; empty array if all are out of stock
     */
    @GetMapping("/in-stock")
    public List<ProductResponse> findInStock() {
        return productService.findInStock();
    }

    /**
     * Lists products whose price falls within the given range.
     *
     * <p>HTTP: {@code GET /api/products/price-range?min=10.00&max=50.00}
     *
     * @param min the minimum price (inclusive)
     * @param max the maximum price (inclusive)
     * @return 200 OK with matching products; empty array if none match
     */
    @GetMapping("/price-range")
    public List<ProductResponse> findByPriceRange(
            @RequestParam BigDecimal min,
            @RequestParam BigDecimal max) {
        return productService.findByPriceRange(min, max);
    }

    // =========================================================================
    // POST endpoint
    // =========================================================================

    /**
     * Creates a new product.
     *
     * <p>HTTP: {@code POST /api/products}
     * <p>{@code @Valid} triggers Bean Validation on the request body. If any
     * constraint is violated, Spring MVC throws {@code MethodArgumentNotValidException}
     * which the {@code GlobalExceptionHandler} converts to a 400 response.
     *
     * @param request the validated product creation request
     * @return 201 Created with the newly created product (including its generated ID)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    // =========================================================================
    // PUT endpoint
    // =========================================================================

    /**
     * Fully replaces an existing product's data.
     *
     * <p>HTTP: {@code PUT /api/products/{id}}
     *
     * @param id      the ID of the product to update (from the URL path)
     * @param request the validated update request
     * @return 200 OK with the updated product; 404 if the ID does not exist
     */
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    // =========================================================================
    // DELETE endpoint
    // =========================================================================

    /**
     * Deletes a product by its ID.
     *
     * <p>HTTP: {@code DELETE /api/products/{id}}
     *
     * @param id the ID of the product to delete
     * @return 204 No Content on success; 404 if the ID does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        // 204 No Content is the standard HTTP response for a successful DELETE
        return ResponseEntity.noContent().build();
    }
}
