package com.example.liquibasemigrations.repository;

import com.example.liquibasemigrations.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for the {@link Product} entity.
 *
 * <p>
 * By extending {@link JpaRepository}, Spring automatically provides all
 * standard CRUD operations (save, findById, findAll, delete, etc.) without
 * any boilerplate SQL or DAO code required.
 * </p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Derived query: Spring Data JPA generates the SQL automatically by
     * parsing the method name. This finds all products that belong to
     * the specified category (case-sensitive match).
     *
     * <p>
     * Generated SQL equivalent: {@code SELECT * FROM products WHERE category = ?}
     * </p>
     *
     * @param category the category name to filter by
     * @return list of products in the given category
     */
    List<Product> findByCategory(String category);
}
