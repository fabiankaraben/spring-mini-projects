package com.example.softdelete.repository;

import com.example.softdelete.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <h2>Soft-delete awareness</h2>
 * <p>Because the {@link Product} entity is annotated with {@code @SQLRestriction("deleted = false")},
 * <strong>every</strong> method defined here (including the ones inherited from
 * {@link JpaRepository}) will automatically filter out soft-deleted rows.
 * No extra {@code WHERE deleted = false} clause is needed in any query method.</p>
 *
 * <h2>Native queries for admin/recovery use cases</h2>
 * <p>The {@code @SQLRestriction} filter is applied at the Hibernate entity level and affects
 * all JPQL / Criteria API queries.  Native SQL queries bypass the entity-level filter, which
 * is useful when an administrator needs to list or restore soft-deleted records.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds all active (non-deleted) products whose name contains the given string,
     * ignoring case.
     *
     * <p>The {@code @SQLRestriction} on {@link Product} ensures that deleted products
     * are automatically excluded without any extra filter clause in this method.</p>
     *
     * @param name substring to search for in the product name
     * @return list of matching active products
     */
    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * Finds all active (non-deleted) products in the given category.
     *
     * @param category the category to filter by
     * @return list of active products in that category
     */
    List<Product> findByCategory(String category);

    /**
     * Checks whether an active product with the exact name exists.
     *
     * @param name exact product name
     * @return {@code true} if at least one active product with that name exists
     */
    boolean existsByName(String name);

    /**
     * Returns all soft-deleted products using a native SQL query.
     *
     * <p>This bypasses the {@code @SQLRestriction} filter because native queries
     * operate directly on the table without going through Hibernate's entity-level
     * WHERE clause injection.  This is useful for admin/audit screens.</p>
     *
     * @return list of products where {@code deleted = true}
     */
    @Query(value = "SELECT * FROM products WHERE deleted = true ORDER BY deleted_at DESC",
           nativeQuery = true)
    List<Product> findAllDeleted();

    /**
     * Finds a soft-deleted product by its ID using a native SQL query.
     *
     * <p>Useful for restore/undelete operations where you need to locate
     * a specific product that has been logically deleted.</p>
     *
     * @param id the product ID
     * @return an {@link Optional} containing the deleted product, or empty if not found
     */
    @Query(value = "SELECT * FROM products WHERE id = :id AND deleted = true",
           nativeQuery = true)
    Optional<Product> findDeletedById(@Param("id") Long id);

    /**
     * Counts the total number of soft-deleted products in the database.
     *
     * <p>Again uses a native query to bypass the entity-level filter.</p>
     *
     * @return count of rows where {@code deleted = true}
     */
    @Query(value = "SELECT COUNT(*) FROM products WHERE deleted = true",
           nativeQuery = true)
    long countDeleted();

    /**
     * Restores a soft-deleted product by resetting its deletion flags.
     *
     * <p>This native UPDATE bypasses the {@code @SQLRestriction} filter (which would
     * prevent Hibernate from finding and updating the deleted row through normal JPA
     * operations) and directly clears {@code deleted} and {@code deleted_at}.</p>
     *
     * <p>The {@code @Modifying} annotation tells Spring Data that this is a DML
     * statement (not a SELECT), and {@code clearAutomatically = true} evicts all
     * cached entities from the current persistence context so that subsequent reads
     * return the freshly restored state from the database.</p>
     *
     * @param id the product ID to restore
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE products SET deleted = false, deleted_at = NULL WHERE id = :id",
           nativeQuery = true)
    void restoreById(@Param("id") Long id);
}
