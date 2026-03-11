package com.example.testcontainerspostgres.repository;

import com.example.testcontainerspostgres.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <p>By extending {@link JpaRepository}, we get the full set of standard CRUD
 * operations for free (findById, findAll, save, deleteById, etc.), plus pagination
 * and sorting. We then add custom query methods below.
 *
 * <p>Spring Data derives the SQL for methods that follow the naming convention
 * (e.g. {@code findByName} → {@code SELECT ... WHERE name = ?}).
 * For more complex queries we use {@code @Query} with JPQL.
 *
 * <p>This interface is also the target of our <b>Testcontainers integration tests</b>
 * in {@code ProductRepositoryIntegrationTest} — every method defined here is
 * exercised against a real PostgreSQL container to ensure correctness.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds a product by its exact name.
     *
     * <p>Spring Data derives the query automatically from the method name:
     * {@code SELECT p FROM Product p WHERE p.name = :name}
     *
     * @param name the exact product name to look up
     * @return an {@link Optional} containing the product, or empty if not found
     */
    Optional<Product> findByName(String name);

    /**
     * Finds all products whose price is less than or equal to the given maximum.
     *
     * <p>Useful for "show me products under $X" queries.
     * Spring Data derives: {@code SELECT p FROM Product p WHERE p.price <= :maxPrice}
     *
     * @param maxPrice the maximum price (inclusive upper bound)
     * @return list of products within the price limit; empty list if none match
     */
    List<Product> findByPriceLessThanEqual(BigDecimal maxPrice);

    /**
     * Finds all products whose name contains the given substring, ignoring case.
     *
     * <p>Supports partial-text search (like a simple search box).
     * Spring Data derives: {@code ... WHERE LOWER(p.name) LIKE LOWER('%keyword%')}
     *
     * @param keyword the substring to search for within product names
     * @return list of matching products; empty list if none match
     */
    List<Product> findByNameContainingIgnoreCase(String keyword);

    /**
     * Checks whether at least one product with the given name exists.
     *
     * <p>More efficient than {@code findByName(...).isPresent()} because it issues
     * a {@code SELECT COUNT(*) > 0} query rather than fetching the full entity.
     *
     * @param name the product name to check
     * @return {@code true} if a product with that name exists
     */
    boolean existsByName(String name);

    /**
     * Returns all products that are currently in stock (stockQuantity > 0).
     *
     * <p>Demonstrates a custom JPQL query using {@code @Query}. JPQL refers to
     * entity field names ({@code p.stockQuantity}), not database column names,
     * which keeps the query portable across databases.
     *
     * @return list of in-stock products; empty list if all products are out of stock
     */
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0")
    List<Product> findAllInStock();

    /**
     * Returns all products with a price between the given bounds (inclusive).
     *
     * <p>JPQL {@code BETWEEN} is inclusive on both ends:
     * {@code WHERE p.price BETWEEN :min AND :max}
     * Named parameters ({@code @Param}) make the JPQL easier to read and maintain.
     *
     * @param min the lower price bound (inclusive)
     * @param max the upper price bound (inclusive)
     * @return list of matching products; empty list if none match
     */
    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :min AND :max")
    List<Product> findByPriceBetween(@Param("min") BigDecimal min, @Param("max") BigDecimal max);
}
