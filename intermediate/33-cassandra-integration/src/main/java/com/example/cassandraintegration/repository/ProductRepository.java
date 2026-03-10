package com.example.cassandraintegration.repository;

import com.example.cassandraintegration.domain.Product;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data Cassandra repository for {@link Product} entities.
 *
 * <p>{@link CassandraRepository} provides standard CRUD operations backed by
 * Cassandra CQL. The primary key type here is a composite of category (String)
 * and id (UUID), represented as an array {@code Object[]} when using the
 * default {@code findById} inherited from {@code CrudRepository}.</p>
 *
 * <h2>Query Design Considerations</h2>
 * <p>Cassandra requires that every query includes the full partition key
 * ({@code category}). Queries that scan without a partition key are
 * "full table scans" (ALLOW FILTERING) and should be avoided in production
 * for large tables. For educational clarity, a few ALLOW FILTERING queries
 * are included and annotated accordingly.</p>
 */
@Repository
public interface ProductRepository extends CassandraRepository<Product, Object> {

    /**
     * Finds all products in a given category.
     *
     * <p>This is the primary access pattern in our wide-column design:
     * category is the partition key, so this query hits a single partition
     * — extremely efficient in Cassandra.</p>
     *
     * @param category the product category (partition key)
     * @return list of products in that category, ordered by UUID ascending
     */
    List<Product> findByCategory(String category);

    /**
     * Finds a specific product by its composite primary key.
     *
     * @param category the partition key
     * @param id       the clustering key (UUID)
     * @return the matching product, or empty if not found
     */
    @Query("SELECT * FROM products WHERE category = ?0 AND id = ?1")
    java.util.Optional<Product> findByCategoryAndId(String category, UUID id);

    /**
     * Finds all products in a category whose price is at or below the given max.
     *
     * <p>Note: Filtering on {@code price} (a non-key column) requires
     * {@code ALLOW FILTERING}. This is acceptable for small partitions
     * but should be avoided on very large datasets.</p>
     *
     * @param category the partition key (required)
     * @param maxPrice the upper price bound (inclusive)
     * @return list of matching products
     */
    @Query("SELECT * FROM products WHERE category = ?0 AND price <= ?1 ALLOW FILTERING")
    List<Product> findByCategoryAndPriceLessThanEqual(String category, BigDecimal maxPrice);

    /**
     * Finds all products in a category with stock above a given threshold.
     *
     * <p>Requires ALLOW FILTERING because {@code stock} is not a key column.</p>
     *
     * @param category   the partition key (required)
     * @param minStock   minimum stock level (exclusive lower bound)
     * @return list of in-stock products
     */
    @Query("SELECT * FROM products WHERE category = ?0 AND stock > ?1 ALLOW FILTERING")
    List<Product> findByCategoryAndStockGreaterThan(String category, int minStock);

    /**
     * Searches for products by name within a category (case-sensitive substring).
     *
     * <p>Uses ALLOW FILTERING + contains pattern. For production workloads,
     * consider Cassandra's SASI index or an external search engine (e.g. Elasticsearch).</p>
     *
     * @param category the partition key
     * @param name     the exact name to match
     * @return list of matching products
     */
    @Query("SELECT * FROM products WHERE category = ?0 AND name = ?1 ALLOW FILTERING")
    List<Product> findByCategoryAndName(String category, String name);
}
