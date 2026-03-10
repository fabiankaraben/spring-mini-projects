package com.example.optimisticlocking.repository;

import com.example.optimisticlocking.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <h2>Optimistic locking at the repository level</h2>
 * <p>No special configuration is needed here – optimistic locking is driven entirely
 * by the {@code @Version} annotation on the {@link Product} entity.  When Hibernate
 * executes an UPDATE through this repository, it automatically:</p>
 * <ul>
 *   <li>Appends {@code AND version = :currentVersion} to the WHERE clause.</li>
 *   <li>Increments the version column in the same statement.</li>
 *   <li>Checks that exactly one row was updated; if zero rows were affected it throws
 *       {@link jakarta.persistence.OptimisticLockException}, which Spring translates to
 *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 * </ul>
 *
 * <p>The service layer catches that exception and maps it to HTTP 409 Conflict
 * via the global exception handler.</p>
 *
 * <p>Extends {@link JpaRepository} to inherit standard CRUD methods:
 * {@code save}, {@code findById}, {@code findAll}, {@code deleteById}, etc.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds all products whose name contains the given substring, ignoring case.
     *
     * <p>Spring Data derives the query from the method name:
     * {@code findBy} + {@code Name} + {@code Containing} + {@code IgnoreCase}
     * → {@code SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%?%')}</p>
     *
     * @param name the substring to search for
     * @return list of matching products
     */
    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * Finds all products in the given category.
     *
     * @param category the category to filter by (case-sensitive)
     * @return list of products in that category
     */
    List<Product> findByCategory(String category);
}
