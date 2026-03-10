package com.example.reactiver2dbc.repository;

import com.example.reactiver2dbc.domain.Product;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Reactive R2DBC repository for {@link Product} entities.
 *
 * <p>Extends {@link ReactiveCrudRepository} which is the reactive counterpart of the
 * traditional {@code CrudRepository} (used with JPA/JDBC). The key difference:
 * <ul>
 *   <li>{@code CrudRepository} (JPA) — blocks the calling thread until the SQL result
 *       arrives from the database. A thread-per-request model is required.</li>
 *   <li>{@code ReactiveCrudRepository} (R2DBC) — returns {@link Mono} or {@link Flux}
 *       types which are lazy, non-blocking publishers. The calling thread is released
 *       immediately; the R2DBC driver invokes a callback when PostgreSQL responds.</li>
 * </ul>
 *
 * <p>Spring Data auto-generates the implementation at startup by:
 * <ol>
 *   <li>Parsing method names (derived queries) — e.g., {@code findByCategory} generates
 *       {@code SELECT * FROM products WHERE category = $1}.</li>
 *   <li>Evaluating {@link Query} annotations — raw SQL for complex queries not expressible
 *       as method names.</li>
 * </ol>
 *
 * <p>Generic type parameters: {@code <Product, Long>}
 * <ul>
 *   <li>First – the entity type managed by this repository.</li>
 *   <li>Second – the type of the entity's {@code @Id} field (PostgreSQL BIGSERIAL as Long).</li>
 * </ul>
 */
@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {

    /**
     * Find all products in a given category (exact, case-sensitive match).
     *
     * <p>Spring Data derives the SQL query {@code SELECT * FROM products WHERE category = $1}
     * from the method name {@code findByCategory}. Returns a {@link Flux} because the result
     * may contain zero or more products.
     *
     * @param category the category to filter by
     * @return a Flux emitting matching products, completing empty if none found
     */
    Flux<Product> findByCategory(String category);

    /**
     * Find all active products (i.e., products visible on the storefront).
     *
     * <p>Derived query: {@code SELECT * FROM products WHERE active = $1}.
     * Pass {@code true} for the active storefront; {@code false} for the admin view
     * of inactive/deactivated products.
     *
     * @param active {@code true} to return active products; {@code false} for inactive
     * @return a Flux emitting products matching the given active status
     */
    Flux<Product> findByActive(boolean active);

    /**
     * Find all products in a given category that are also active.
     *
     * <p>Derived query combining two predicates:
     * {@code SELECT * FROM products WHERE category = $1 AND active = $2}.
     * Useful for the public-facing category listing page.
     *
     * @param category the category to filter by
     * @param active   whether the products must be active
     * @return a Flux emitting matching products
     */
    Flux<Product> findByCategoryAndActive(String category, boolean active);

    /**
     * Find all products whose price falls within an inclusive range [min, max].
     *
     * <p>Uses a {@link Query} annotation with a named SQL query containing positional
     * parameters ({@code $1}, {@code $2}) which R2DBC replaces at execution time.
     * This demonstrates raw SQL queries when derived method names become too verbose.
     *
     * @param min the minimum price (inclusive)
     * @param max the maximum price (inclusive)
     * @return a Flux emitting products within the price range, ordered by price ascending
     */
    @Query("SELECT * FROM products WHERE price >= :min AND price <= :max ORDER BY price ASC")
    Flux<Product> findByPriceBetween(BigDecimal min, BigDecimal max);

    /**
     * Find all products whose name contains the given text, case-insensitively.
     *
     * <p>Uses PostgreSQL's {@code ILIKE} operator (case-insensitive LIKE). The {@code %}
     * wildcard characters are added in the service layer before calling this method so the
     * SQL remains clean and readable here.
     *
     * @param namePattern the SQL LIKE pattern (e.g., {@code %keyboard%})
     * @return a Flux emitting products whose name matches the pattern
     */
    @Query("SELECT * FROM products WHERE name ILIKE :namePattern")
    Flux<Product> findByNameLike(String namePattern);

    /**
     * Count the total number of products in a given category.
     *
     * <p>Aggregates without loading all rows into memory.
     * Returns a {@link Mono} because a count is always a single value.
     *
     * @param category the category to count products for
     * @return a Mono emitting the count of products in that category
     */
    Mono<Long> countByCategory(String category);

    /**
     * Find all products with stock quantity at or below the given threshold.
     *
     * <p>Useful for inventory management — triggers restocking alerts when products
     * are running low. Uses a {@link Query} annotation with a named parameter.
     *
     * @param threshold the maximum stock quantity to include (e.g., 5 for "low stock")
     * @return a Flux emitting products with low stock, ordered by stock quantity ascending
     */
    @Query("SELECT * FROM products WHERE stock_quantity <= :threshold ORDER BY stock_quantity ASC")
    Flux<Product> findLowStock(int threshold);
}
