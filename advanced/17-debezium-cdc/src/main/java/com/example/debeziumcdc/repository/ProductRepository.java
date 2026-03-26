package com.example.debeziumcdc.repository;

import com.example.debeziumcdc.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <p>Spring Data auto-generates the implementation at runtime, providing
 * standard CRUD operations inherited from {@link JpaRepository}:
 * <ul>
 *   <li>{@code save(product)} — INSERT (new) or UPDATE (existing).</li>
 *   <li>{@code findById(id)} — SELECT by primary key.</li>
 *   <li>{@code findAll()} — SELECT all products.</li>
 *   <li>{@code deleteById(id)} — DELETE by primary key.</li>
 * </ul>
 *
 * <p>Every database write through this repository is persisted to the
 * PostgreSQL {@code products} table. Debezium listens on the WAL and
 * captures these changes as CDC events streamed to Kafka.
 *
 * <p>Custom query methods are derived from their names by Spring Data:
 * <ul>
 *   <li>{@link #findByNameContainingIgnoreCase(String)} — search by name substring.</li>
 *   <li>{@link #findByPriceLessThanEqual(BigDecimal)} — filter by max price.</li>
 *   <li>{@link #findByStockGreaterThan(int)} — find in-stock products.</li>
 * </ul>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds all products whose name contains the given substring (case-insensitive).
     *
     * <p>Spring Data generates: {@code SELECT * FROM products WHERE LOWER(name) LIKE %keyword%}
     *
     * @param keyword the search substring
     * @return list of matching products
     */
    List<Product> findByNameContainingIgnoreCase(String keyword);

    /**
     * Finds all products whose price is less than or equal to the given maximum.
     *
     * <p>Spring Data generates: {@code SELECT * FROM products WHERE price <= maxPrice}
     *
     * @param maxPrice the maximum price (inclusive)
     * @return list of matching products
     */
    List<Product> findByPriceLessThanEqual(BigDecimal maxPrice);

    /**
     * Finds all products with stock greater than the given minimum.
     *
     * <p>Useful for finding products that are currently in stock.
     * Spring Data generates: {@code SELECT * FROM products WHERE stock > minStock}
     *
     * @param minStock the minimum stock level (exclusive)
     * @return list of matching products
     */
    List<Product> findByStockGreaterThan(int minStock);
}
