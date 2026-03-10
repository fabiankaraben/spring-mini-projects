package com.example.batchschedulers.repository;

import com.example.batchschedulers.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <p>Spring Data automatically generates the SQL queries at startup based on
 * method names and the entity's field names.
 *
 * <p>This repository is used by:
 * <ul>
 *   <li>The batch jobs' {@code ItemReader}s to load products for processing.</li>
 *   <li>The {@code BatchJobController} to expose product data via REST.</li>
 *   <li>Integration tests to assert database state after job execution.</li>
 * </ul>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Returns all products belonging to the specified category.
     *
     * @param category the category to filter on (case-sensitive)
     * @return list of products in that category, or empty list if none
     */
    List<Product> findByCategory(String category);

    /**
     * Returns all products that are currently flagged as low-stock.
     *
     * @param lowStock {@code true} to find low-stock products
     * @return list of products with the given low-stock flag
     */
    List<Product> findByLowStock(boolean lowStock);

    /**
     * Finds a single product by its unique name.
     *
     * @param name the exact product name
     * @return an {@code Optional} containing the product, or empty if not found
     */
    Optional<Product> findByName(String name);

    /**
     * Counts the number of products currently flagged as low-stock.
     *
     * @param lowStock {@code true} to count low-stock products
     * @return the count
     */
    long countByLowStock(boolean lowStock);
}
