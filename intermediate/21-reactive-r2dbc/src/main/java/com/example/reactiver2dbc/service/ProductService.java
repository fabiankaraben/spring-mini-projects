package com.example.reactiver2dbc.service;

import com.example.reactiver2dbc.domain.Product;
import com.example.reactiver2dbc.dto.ProductRequest;
import com.example.reactiver2dbc.repository.ProductRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Service layer containing the business logic for product management.
 *
 * <p>This class sits between the HTTP layer ({@link com.example.reactiver2dbc.controller.ProductController})
 * and the data access layer ({@link ProductRepository}). Key responsibilities:
 * <ul>
 *   <li>Map request DTOs ({@link ProductRequest}) to domain entities ({@link Product}).</li>
 *   <li>Orchestrate reactive streams using {@link Mono} and {@link Flux} operators.</li>
 *   <li>Encapsulate business rules (e.g., preserving {@code createdAt} on update,
 *       building LIKE patterns for name search).</li>
 * </ul>
 *
 * <p><strong>Reactive programming model:</strong><br>
 * Every method returns a {@link Mono} (0 or 1 item) or a {@link Flux} (0..N items).
 * These are <em>lazy cold publishers</em> — no SQL is executed until a subscriber
 * subscribes. The WebFlux controller subscribes when it serialises the response.
 * This means no SQL is issued if, for example, the route is never hit.
 *
 * <p><strong>Key R2DBC vs JPA behaviours to understand:</strong>
 * <ul>
 *   <li>R2DBC {@code save()} checks the entity's {@code @Id}: if {@code null} → INSERT;
 *       if non-null → UPDATE. Unlike JPA, there is no persistent context / dirty checking.</li>
 *   <li>There are no lazy-loaded relationships in R2DBC — every join must be explicit
 *       (via {@code @Query} or a custom repository method).</li>
 *   <li>Transactions work via {@code @Transactional} from Spring's reactive transaction
 *       manager, which uses Project Reactor's context to propagate the transaction across
 *       async boundaries (rather than ThreadLocal as in JDBC).</li>
 * </ul>
 *
 * <p><strong>Operator glossary used in this class:</strong>
 * <ul>
 *   <li>{@code flatMap} – transform each item into a new publisher and flatten results.</li>
 *   <li>{@code map} – transform each item synchronously (no new publisher).</li>
 *   <li>{@code switchIfEmpty} – emit a fallback publisher when the upstream is empty.</li>
 * </ul>
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Constructor injection makes the dependency explicit and enables unit testing
     * without a Spring context (just pass a mock repository).
     *
     * @param productRepository reactive R2DBC repository for products
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all products as a reactive stream.
     *
     * <p>Returns a {@link Flux} that emits each row as it arrives from PostgreSQL —
     * back-pressure is automatically handled so the application never loads the entire
     * table into heap memory at once.
     *
     * @return a Flux emitting all products
     */
    public Flux<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Retrieve a single product by its primary key.
     *
     * <p>Returns a {@link Mono} that either emits the product or completes empty.
     * The controller maps an empty Mono to HTTP 404.
     *
     * @param id the product's primary key (BIGSERIAL Long)
     * @return a Mono emitting the product, or empty if not found
     */
    public Mono<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Find all products in a given category.
     *
     * @param category the category to filter by (case-sensitive)
     * @return a Flux emitting matching products
     */
    public Flux<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    /**
     * Find all active products (visible on the storefront).
     *
     * @return a Flux emitting only active products
     */
    public Flux<Product> findActive() {
        return productRepository.findByActive(true);
    }

    /**
     * Find all active products within a specific category.
     *
     * <p>Useful for category pages on the storefront where only active products
     * should be displayed to customers.
     *
     * @param category the category to filter by
     * @return a Flux emitting active products in the given category
     */
    public Flux<Product> findActiveByCategoryAndActive(String category) {
        return productRepository.findByCategoryAndActive(category, true);
    }

    /**
     * Find all products whose price falls within an inclusive range [min, max].
     *
     * @param min the minimum price (inclusive)
     * @param max the maximum price (inclusive)
     * @return a Flux emitting matching products ordered by price ascending
     */
    public Flux<Product> findByPriceRange(BigDecimal min, BigDecimal max) {
        return productRepository.findByPriceBetween(min, max);
    }

    /**
     * Search products by keyword in their name (case-insensitive).
     *
     * <p>Wraps the keyword in SQL {@code %} wildcard characters to perform a
     * substring match. PostgreSQL's {@code ILIKE} operator handles case-insensitivity
     * without requiring any normalisation of the keyword before storing.
     *
     * @param keyword text to search for within product names
     * @return a Flux emitting products whose names contain the keyword
     */
    public Flux<Product> searchByName(String keyword) {
        // Wrap with % wildcards for a SQL ILIKE substring match.
        // This is done in the service (not the repository) to keep the SQL clean.
        String pattern = "%" + keyword + "%";
        return productRepository.findByNameLike(pattern);
    }

    /**
     * Count the total number of products in a given category.
     *
     * @param category the category to count products for
     * @return a Mono emitting the count
     */
    public Mono<Long> countByCategory(String category) {
        return productRepository.countByCategory(category);
    }

    /**
     * Find all products with stock quantity at or below the given threshold.
     *
     * <p>Useful for generating restocking alerts in inventory management dashboards.
     *
     * @param threshold the low-stock threshold (e.g., 5)
     * @return a Flux emitting low-stock products ordered by stock quantity ascending
     */
    public Flux<Product> findLowStock(int threshold) {
        return productRepository.findLowStock(threshold);
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create and persist a new product row in PostgreSQL.
     *
     * <p>Maps the request DTO to a new {@link Product} domain object. The {@code id}
     * is left {@code null} so PostgreSQL generates a BIGSERIAL value. The
     * {@code createdAt} and {@code updatedAt} fields are populated by Spring Data
     * auditing ({@code @CreatedDate} / {@code @LastModifiedDate}).
     *
     * <p>Reactive pipeline: {@code save(entity)} returns a {@link Mono} that emits
     * the saved entity (with the generated {@code id} and audit timestamps) on completion.
     *
     * @param request the product data from the HTTP request body
     * @return a Mono emitting the persisted product with its generated id
     */
    public Mono<Product> create(ProductRequest request) {
        // Map DTO → domain entity; id is null so PostgreSQL assigns a BIGSERIAL value
        Product product = new Product(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getCategory(),
                request.getStockQuantity(),
                request.isActive()
        );
        // save() detects id == null → issues INSERT; emits the saved entity with id set
        return productRepository.save(product);
    }

    /**
     * Update an existing product row (full replacement — PUT semantics).
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code findById(id)} — look up the existing row; returns empty Mono if absent.</li>
     *   <li>{@code map(...)} — mutate the entity in-place with new field values.</li>
     *   <li>{@code flatMap(save)} — persist the mutated entity; R2DBC issues an UPDATE
     *       because the id is non-null.</li>
     * </ol>
     * Returns an empty {@link Mono} if the product is not found; the controller maps
     * this to HTTP 404.
     *
     * <p>Note: {@code createdAt} is intentionally NOT updated here. The {@code @LastModifiedDate}
     * annotation on {@code updatedAt} ensures it is refreshed automatically by Spring
     * Data auditing on every save.
     *
     * @param id      the product's primary key
     * @param request the new field values
     * @return a Mono emitting the updated product, or empty if not found
     */
    public Mono<Product> update(Long id, ProductRequest request) {
        return productRepository.findById(id)
                // map() applies synchronous mutations — no new publisher is created here
                .map(existing -> {
                    existing.setName(request.getName());
                    existing.setDescription(request.getDescription());
                    existing.setPrice(request.getPrice());
                    existing.setCategory(request.getCategory());
                    existing.setStockQuantity(request.getStockQuantity());
                    existing.setActive(request.isActive());
                    // createdAt is left unchanged; updatedAt is refreshed by @LastModifiedDate
                    return existing;
                })
                // flatMap saves the mutated entity; emits the updated row downstream
                .flatMap(productRepository::save);
    }

    /**
     * Delete a product row by its primary key.
     *
     * <p>Reactive pipeline:
     * <ol>
     *   <li>{@code findById(id)} — verify the product exists.</li>
     *   <li>{@code flatMap(deleteById)} — delete only if found; emits {@code true}.</li>
     *   <li>{@code switchIfEmpty(Mono.just(false))} — emit {@code false} if the product
     *       was not found, so the controller can respond with HTTP 404 instead of 204.</li>
     * </ol>
     *
     * @param id the product's primary key
     * @return a Mono emitting {@code true} if deleted, or {@code false} if not found
     */
    public Mono<Boolean> deleteById(Long id) {
        return productRepository.findById(id)
                // Only delete if the product actually exists
                .flatMap(product -> productRepository.deleteById(product.getId())
                        // deleteById returns Mono<Void>; thenReturn maps it to true
                        .thenReturn(true))
                // If findById was empty, emit false to signal "not found"
                .switchIfEmpty(Mono.just(false));
    }
}
